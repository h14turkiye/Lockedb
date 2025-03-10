package com.h14turkiye.lockedb.redis;

import com.h14turkiye.lockedb.ALock;
import com.h14turkiye.lockedb.ALockBuilder;
import io.lettuce.core.RedisClient;

/**
 * A builder class for creating Redis-based locks.
 */
public class RedisLockBuilder extends ALockBuilder {

    private RedisClient redisClient;

    public RedisLockBuilder(RedisClient redisClient) {
        this.redisClient = redisClient;
    }

    /**
     * Builds a new RedisLock instance with the specified key.
     *
     * @param key the unique key for the lock
     * @return a new instance of RedisLock
     */
    public ALock build(final String key) {
        RedisLock lock = new RedisLock(redisClient, key);
        lock.setPassword(password);
        lock.setExpiresAfterMS(expiresAfterMS);
        lock.setTimeoutMS(timeoutMS);
        return lock;
    }
}