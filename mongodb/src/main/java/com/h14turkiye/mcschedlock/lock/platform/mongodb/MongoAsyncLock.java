package com.h14turkiye.mcschedlock.lock.platform.mongodb;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.bson.Document;

import com.h14turkiye.mcschedlock.lock.AsyncLock;
import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.wrapper.task.WrappedTask;

@SuppressWarnings("deprecation")
public class MongoAsyncLock extends AsyncLock {
    private static MongoDatabase database;
    private static MongoCollection<Document> locksCollection;
    private WrappedTask intervalTask;
    
    public MongoAsyncLock(String key) {
        super(key);
        initialize();
    }
    
    public MongoAsyncLock(String key, String bypassToken) {
        super(key, bypassToken);
        initialize();
    }
    
    private void initialize() {
        if (database == null) {
            database = (MongoDatabase) MongoLockConnect.getDatabase();
            locksCollection = database.listCollectionNames().into(new ArrayList<>()).contains("locks") ? 
            database.getCollection("locks") : createLocksCollection();
            FoliaLib foliaLib = new FoliaLib(plugin);
            scheduler = foliaLib.getScheduler();
        }
    }
    
    private MongoCollection<Document> createLocksCollection() {
        database.createCollection("locks", new CreateCollectionOptions());
        return database.getCollection("locks");
    }
    
    @Override
    public boolean I_IMPLEMENTED_CONSTRUCTORS_AND_CONFIGURATIONS() {
        return true;
    }
    
    @Override
    public CompletableFuture<Boolean> check() {
        return CompletableFuture.supplyAsync(() -> {
            long currentTime = System.currentTimeMillis();
            Document lock = locksCollection.find(new Document("_id", key)).first();
            return lock == null || lock.getLong("expiresAt") < currentTime || (bypassToken != null && bypassToken.equals(lock.getString("bypassToken")));
        });
    }
    
    @Override
    public AsyncLock acquire() {
        attemptLockAcquisition(null).thenAccept(acquired -> {
            if (!acquired)
            scheduler.runTimerAsync(this::attemptLockAcquisition, intervalMS, intervalMS, TimeUnit.MILLISECONDS);
        });
        return this;
    }
    
    private CompletableFuture<Boolean> attemptLockAcquisition(WrappedTask task) {
        if (intervalTask == null) {
            intervalTask = task;
        }
        return tryAcquireLock().thenApply(acquired -> {
            if (acquired) {
                completeLockAcquisition(task);
                return true;
            } else {
                retryLockAcquisition(task);
                return false;
            }
        }).exceptionally(e -> {
            plugin.getLogger().severe("Error acquiring lock for key: " + key);
            e.printStackTrace();
            task.cancel(); // Stop retries if exception occurs
            return null;
        });
    }
    
    private void completeLockAcquisition(WrappedTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel(); // Cancel the retry task
        }
        
        processAsyncSyncTasks(onLockAcquiredAsyncRunnable, onLockAcquiredSyncRunnable, true);
        
    }
    
    private void retryLockAcquisition(WrappedTask task) {
        processAsyncSyncTasks(onLockTryAcquireFailedAsyncRunnable, onLockTryAcquireFailedSyncRunnable, false);
        
        if (retryCount == maxRetry)
        task.cancel();
        
        retryCount++;
    }
    
    private void processAsyncSyncTasks(Runnable onLockAcquiredAsyncRunnable, Runnable onLockAcquiredSyncRunnable, boolean checkLockTaskCompletion) {
        if (intervalTask == null || (!intervalTask.isCancelled())) {
            if (onLockAcquiredAsyncRunnable != EMPTY_RUNNABLE)
            CompletableFuture.runAsync(() -> onLockAcquiredAsyncRunnable.run())
            .thenRun(() -> checkLockTaskCompletion(checkLockTaskCompletion));
            else checkLockTaskCompletion(checkLockTaskCompletion);
            
            if (onLockAcquiredSyncRunnable != EMPTY_RUNNABLE)
            scheduler.runNextTick((task) -> {
                onLockAcquiredSyncRunnable.run();
                checkLockTaskCompletion(checkLockTaskCompletion);
            });
            else checkLockTaskCompletion(checkLockTaskCompletion);
        }
    }
    
    private void checkLockTaskCompletion(boolean checkLockTaskCompletion) {
        if (!checkLockTaskCompletion) return;
        if (++onLockTaskCompletedAmount == 2) {
            release();
        }
    }
    
    private CompletableFuture<Boolean> tryAcquireLock() {
        return check().thenApplyAsync(canAcquire -> {
            long currentTime = System.currentTimeMillis();
            long newExpiration = currentTime + expiresAfterMS;
            Document filter = new Document("_id", key);
            try {
                Document update = new Document("$set", new Document("expiresAt", newExpiration).append("bypassToken", bypassToken));
                FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.BEFORE);
                Document previous = locksCollection.findOneAndUpdate(filter, update, options);
                return previous == null || previous.getLong("expiresAt") < currentTime || (bypassToken != null && bypassToken.equals(previous.getString("bypassToken")));
            } catch (MongoCommandException e) {
                if (e.getErrorCode() == 11000) { // Duplicate id, which means key is already locked
                    // get if bypassToken is same, if same then return true
                    plugin.getLogger().info("Duplicate try acquire lock for key: " + key);
                    if (bypassToken == null) return false;
                    Document lock = locksCollection.find(filter).first();
                    return lock != null  && bypassToken.equals(lock.getString("bypassToken"));
                }
                return false;
            }
        });
    }
    
    @Override
    public void release() {
        try {
            if (intervalTask != null)
            intervalTask.cancel();
            
            CompletableFuture.runAsync(() -> {
                if (locksCollection.deleteOne(new Document("_id", key)).getDeletedCount() != 1)
                throw new RuntimeException("Error releasing lock for key !=1: " + key);
            });
        } catch (Exception e) {
            plugin.getLogger().severe("Error releasing lock for key: " + key);
            e.printStackTrace();
        }
    }
    
    @Override
    public CompletableFuture<Boolean> exist() {
        return CompletableFuture.supplyAsync(() -> findKey(key) != null);
    }
    
    private static Document findKey(String key) {
        return locksCollection.find(new Document("_id", key)).first();
    }
}
