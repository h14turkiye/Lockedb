package com.h14turkiye.lockedb.redis;

import java.util.concurrent.CompletableFuture;

import com.h14turkiye.lockedb.ALock;
import com.h14turkiye.lockedb.ALockBuilder;
import com.h14turkiye.lockedb.LockFactory;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;

public class RedisLockFactory implements LockFactory {
    private final RedisClient redisClient;
    
    /**
     * Constructs a RedisLockFactory with the specified RedisClient.
     *
     * @param redisClient the RedisClient instance to handle Redis connections
     */
    public RedisLockFactory(RedisClient redisClient) {
        this.redisClient = redisClient;
        initializeRedisKeyspace();
    }
    
    // Builder pattern approach
    public ALockBuilder builder() {
        return new RedisLockBuilder();
    }
    
    // Direct creation approach
    public ALock createLock(String key) {
        return new RedisLock(key);
    }

    private RedisAsyncCommands<String, String> rcommands;
    
    /**
     * Initializes Redis for lock functionality. 
     * This is where we would set up any Redis-specific configurations.
     * There's no direct equivalent to MongoDB's collection creation and indexing,
     * but we can ensure Redis is properly connected and configure key notifications.
     */
    private void initializeRedisKeyspace() {
        // Start the watch mechanism for lock notifications
        RedisLock._startWatch(redisClient);
        
        // Test connection and check Redis version to ensure compatibility
        RedisCommands<String, String> commands = redisClient.connect().sync();
        String info = commands.info("server");
        
        // Verify Redis version is compatible (Redis 2.6+ for Lua scripts)
        // This is just a basic check - production code would parse the version more carefully
        if (!info.contains("redis_version:")) {
            throw new RuntimeException("Unable to retrieve Redis server information");
        }
        
        // For Redis, we don't need to create "collections" or indexes
        // Keys will expire based on the TTL we set during lock acquisition
        rcommands = redisClient.connect().async();
    }

    @Override
    public CompletableFuture<String> getPassword(String key) {
        return rcommands.get(key).toCompletableFuture();
    }
}