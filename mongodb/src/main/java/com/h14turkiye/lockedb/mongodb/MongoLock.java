package com.h14turkiye.lockedb.mongodb;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bson.Document;

import com.h14turkiye.lockedb.ALock;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.OperationType;

public class MongoLock extends ALock {
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, new ThreadFactory() {
        @Override
        public Thread newThread(final Runnable r) {
            final Thread thread = new Thread(r);
            thread.setDaemon(true);  // Ensures it does not block JVM shutdown
            return thread;
        }
    });
    
    // Use a ConcurrentHashMap for thread-safe operations and faster lookups.
    public static ConcurrentMap<String, MongoLock> _retryingLocks = new ConcurrentHashMap<>();

    public static void _startWatch(final MongoCollection<Document> collection) {
        scheduler.submit(() -> {
            collection.watch().forEach((Consumer<ChangeStreamDocument<Document>>) event -> {
                if (event.getOperationType().equals(OperationType.DELETE)) {
                    final String documentKey = event.getDocumentKey().getString("_id").getValue();
                    final MongoLock lock = _retryingLocks.get(documentKey);
                    if (lock != null) {
                        lock.attemptLockAcquisition();
                        _retryingLocks.remove(documentKey);
                    }
                }
            });
        });
    }
    
    private final MongoCollection<Document> locksCollection;
    private CompletableFuture<Boolean> acquireFuture;
    
    private final UUID uuid = UUID.randomUUID();
    
    public MongoLock(final MongoCollection<Document> locksCollection, final String key) {
        this.locksCollection = locksCollection;
        this.key = key;
    }

    public CompletableFuture<Boolean> release() {
        acquireFuture.complete(null);
        try {
            return CompletableFuture.supplyAsync(() -> {
                return (locksCollection.deleteOne(new Document("_id", key).append("password", password).append("uuid", uuid.toString())).getDeletedCount() == 1);
            });
            
        } catch (final Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public CompletableFuture<Boolean> isLocked() {
        return CompletableFuture.supplyAsync(() -> findKey(key) != null);
    }
    
    public CompletableFuture<Boolean> isAcquirable() {
        return CompletableFuture.supplyAsync(() -> {
            final long currentTime = System.currentTimeMillis();
            final Document lock = locksCollection.find(new Document("_id", key)).first();
            return lock == null || lock.getLong("expires") < currentTime || (password != null && password.equals(lock.getString("password")));
        });
    }
    
    public CompletableFuture<Boolean> acquire() {
        acquireFuture = new CompletableFuture<>();
        attemptLockAcquisition();
        return acquireFuture;
    }
    
    private Document findKey(final String key) {
        return locksCollection.find(new Document("_id", key)).first();
    }
    
    private CompletableFuture<Void> attemptLockAcquisition() {
        return CompletableFuture.runAsync(() -> {
            if (acquireFuture.isDone()) {
                // [DEBUG] Lock already determined as non-acquirable.
                return;
            }
            
            if (timeoutMS > 0) {
                // [DEBUG] Scheduling lock timeout in " + timeoutMS + "ms
                scheduler.schedule(() -> acquireFuture.complete(false), timeoutMS, TimeUnit.MILLISECONDS);
            }
            
            if (password != null) { // If the password exists
                // [DEBUG] Checking if lock is acquirable...
                this.isAcquirable().thenAcceptAsync((bool) -> {
                    // [DEBUG] isAcquirable result: 
                    if (bool) {
                        Document filter = new Document("_id", key).append("password", password);
                        final Document update = new Document("$set", new Document("expires", System.currentTimeMillis() + expiresAfterMS).append("uuid", uuid.toString()).append("password", password).append("_id", key));
                        // [DEBUG] Updating lock document in MongoDB.
                        locksCollection.updateOne(filter, update, new UpdateOptions().upsert(true));
                        acquireFuture.complete(true);
                        scheduleExpirationRemoval();
                    }
                });
                if (acquireFuture.isDone()) {
                    // [DEBUG] Lock acquired via password match.
                    return;
                }
            }
            
            final Document lockDoc = new Document("_id", key)
                .append("password", password)
                .append("uuid", uuid.toString())
                .append("expires", System.currentTimeMillis() + expiresAfterMS);
    
            try {
                // [DEBUG] Attempting to create lock document.
                locksCollection.insertOne(lockDoc);
                // [DEBUG] Lock successfully acquired.
    
                scheduleExpirationRemoval();
                acquireFuture.complete(true);
                return;
            } catch (Exception e) {
                if (e instanceof MongoWriteException && ((MongoWriteException) e).getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                    // [ERROR] Lock acquisition failed, adding to retrying locks: 
                    _retryingLocks.put(key, this);
                }
                else {
                    e.printStackTrace();
                }
            }
        });
    }    
    
    private void scheduleExpirationRemoval() {
        // Schedule a task to remove the document if expiration exists
        if (expiresAfterMS > 0) {
            scheduler.schedule(this::release, expiresAfterMS, TimeUnit.MILLISECONDS);
        }
    }
    
}
