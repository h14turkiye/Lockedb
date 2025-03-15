package com.h14turkiye.lockedb.mongodb;

import com.h14turkiye.lockedb.ALock;
import com.h14turkiye.lockedb.ALockBuilder;

/**
 * A builder class for creating MongoDB-based locks.
 */
public class MongoLockBuilder extends ALockBuilder {
    /**
     * Builds a new MongoLock instance with the specified key.
     *
     * @param key the unique key for the lock
     * @return a new instance of MongoLock
     */
    public ALock build(final String key) {
        MongoLock lock = new MongoLock(key);
        lock.setPassword(password);
        lock.setExpiresAfterMS(expiresAfterMS);
        lock.setTimeoutMS(timeoutMS);
        return lock;
    }
}
