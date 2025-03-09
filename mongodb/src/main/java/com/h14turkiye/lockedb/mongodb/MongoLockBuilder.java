package com.h14turkiye.lockedb.mongodb;

import org.bson.Document;

import com.h14turkiye.lockedb.ALock;
import com.h14turkiye.lockedb.ALockBuilder;
import com.mongodb.client.MongoCollection;

/**
 * A builder class for creating MongoDB-based locks.
 */
public class MongoLockBuilder extends ALockBuilder {

    private MongoCollection<Document> locksCollection;

    public MongoLockBuilder(MongoCollection<Document> locksCollection) {
        this.locksCollection = locksCollection;
    }

    /**
     * Builds a new MongoLock instance with the specified key.
     *
     * @param key the unique key for the lock
     * @return a new instance of MongoLock
     */
    public ALock build(final String key) {
        MongoLock lock = new MongoLock(locksCollection, key);
        lock.setPassword(password);
        lock.setExpiresAfterMS(expiresAfterMS);
        lock.setTimeoutMS(timeoutMS);
        return lock;
    }
}
