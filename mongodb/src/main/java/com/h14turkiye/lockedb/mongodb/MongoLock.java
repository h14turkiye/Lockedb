package com.h14turkiye.lockedb.mongodb;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
    // Use a ConcurrentHashMap for thread-safe operations and faster lookups.
    public static ConcurrentMap<String, MongoLock> _retryingLocks = new ConcurrentHashMap<>();
    
    public static void _startWatch(final MongoCollection<Document> collection) {
        MongoLock.locksCollection = collection;
        executor.submit(() -> {
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
    
    private static MongoCollection<Document> locksCollection;
    
    
    
    public MongoLock(final String key) {
        this.key = key;
    }
    
    public CompletableFuture<Boolean> release() {
        acquireFuture.complete(null);
        try {
            return CompletableFuture.supplyAsync(() -> {
                return (locksCollection.deleteOne(new Document("_id", key).append("password", password).append("uuid", uuid)).getDeletedCount() == 1);
            }, executor);
            
        } catch (final Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public CompletableFuture<Boolean> isLocked() {
        return CompletableFuture.supplyAsync(() -> findKey(key) != null, executor);
    }
    
    public CompletableFuture<Boolean> isAcquirable() {
        return CompletableFuture.supplyAsync(() -> {
            long currentTime = System.currentTimeMillis();
            Document lock = locksCollection.find(new Document("_id", key)).first();
            
            if (lock == null) return true;
            
            long expires = (long) lock.getOrDefault("expires", 0L);
            return expires < currentTime || (password != null && password.equals(lock.getString("password")));
        }, executor);
    }
    
    
    public CompletableFuture<Boolean> acquire() {
        acquireFuture = new CompletableFuture<>();
        attemptLockAcquisition();
        return acquireFuture;
    }
    
    private Document findKey(final String key) {
        return locksCollection.find(new Document("_id", key)).first();
    }
    
    private void attemptLockAcquisition() {
        if (acquireFuture.isDone()) {
            // [DEBUG] Lock already determined as non-acquirable.
            return;
        }
        
        if (timeoutMS > 0) {
            // [DEBUG] Scheduling lock timeout in " + timeoutMS + "ms
            schedule(() -> acquireFuture.complete(false), timeoutMS);
        }
        
        // [DEBUG] Checking if lock is acquirable...
        this.isAcquirable().thenApplyAsync((bool) -> {
            // [DEBUG] isAcquirable result: 
            if (password != null && bool) {
                Document filter = new Document("_id", key).append("password", password);
                final Document update = new Document("$set", new Document("expires", System.currentTimeMillis() + expiresAfterMS).append("uuid", uuid).append("password", password).append("_id", key));
                // [DEBUG] Updating lock document in MongoDB.
                locksCollection.updateOne(filter, update, new UpdateOptions().upsert(true));
                acquireFuture.complete(true);
                scheduleExpirationRemoval();
                return true;
            }
            return false;
        }, executor).thenAcceptAsync(passwordAcquired -> {
            if (passwordAcquired) {
                return;
            }
            
            final Document lockDoc = new Document("_id", key)
            .append("password", password)
            .append("uuid", uuid)
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
        }, executor);
        
        
    }    
    
}
