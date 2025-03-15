package com.h14turkiye.lockedb.redis;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.h14turkiye.lockedb.ALock;
import io.lettuce.core.RedisClient;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.ScriptOutputType;

public class RedisLock extends ALock {
    // TTL buffer to ensure our release() runs before Redis auto-expires the key
    private static final long TTL_BUFFER_MS = 5;
    
    // Use a ConcurrentHashMap for thread-safe operations and faster lookups
    public static ConcurrentMap<String, RedisLock> _retryingLocks = new ConcurrentHashMap<>();
    
    // Redis channel for lock events
    private static final String LOCK_CHANNEL = "lock_events";
    
    public static void _startWatch(final RedisClient redisClient) {
        StatefulRedisPubSubConnection<String, String> pubSubConnection = redisClient.connectPubSub();
        RedisLock.redisCommands = redisClient.connect().async();
        pubSubConnection.addListener(new RedisPubSubAdapter<String, String>() {
            @Override
            public void message(String channel, String message) {
                if (LOCK_CHANNEL.equals(channel) && message.startsWith("DELETE:")) {
                    String key = message.substring(7); // Remove "DELETE:" prefix
                    final RedisLock lock = _retryingLocks.get(key);
                    if (lock != null) {
                        lock.attemptLockAcquisition();
                        _retryingLocks.remove(key);
                    }
                }
            }
        });
        pubSubConnection.async().subscribe(LOCK_CHANNEL);
    }
    
    private static RedisAsyncCommands<String, String> redisCommands;
    
    public RedisLock(final String key) {
        this.key = key;
    }
    
    public CompletableFuture<Boolean> release() {
        acquireFuture.complete(null);
        String lockValue = uuid + (password != null ? ":" + password : "");
        String script =
        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
        "  redis.call('del', KEYS[1]) " +
        "  redis.call('publish', KEYS[2], 'DELETE:' .. KEYS[1]) " +
        "  return 1 " +
        "else " +
        "  return 0 " +
        "end";
        
        return redisCommands.eval(
        script,
        ScriptOutputType.INTEGER,
        new String[]{key, LOCK_CHANNEL},
        lockValue
        ).toCompletableFuture()
        .thenApply(result -> result != null && (Long) result == 1L)
        .exceptionally(ex -> {
            ex.printStackTrace();
            return false;
        });
    }
    
    public CompletableFuture<Boolean> isLocked() {
        return redisCommands.get(key).toCompletableFuture().thenApplyAsync(value -> value != null, executor);
    }
    
    public CompletableFuture<Boolean> isAcquirable() {
        return redisCommands.get(key).toCompletableFuture().thenComposeAsync((value) -> {
            
            if (value == null) {
                return CompletableFuture.completedFuture(true); // Lock doesn't exist
            }
            
            // Check if it's our lock with our password
            if (password != null && value.endsWith(":" + password)) {
                return CompletableFuture.completedFuture(true);
            }
            
            // Check TTL to see if it's expired
            return redisCommands.ttl(key).toCompletableFuture().thenApply(ttl -> ttl <= 0); // Expired or no TTL set
        }, executor);
    }
    
    public CompletableFuture<Boolean> acquire() {
        acquireFuture = new CompletableFuture<>();
        attemptLockAcquisition();
        return acquireFuture;
    }
    
    private void attemptLockAcquisition() {
        if (acquireFuture.isDone()) return;
        
        // Set up the timeout if needed
        if (timeoutMS > 0) {
            schedule(() -> acquireFuture.complete(false), timeoutMS);
        }
        
        String lockValue = uuid + (password != null ? ":" + password : "");
        
        // Password-protected lock attempt
        this.isAcquirable().thenComposeAsync((bool) -> {
            if (password != null && bool) {
                long redisTTL = expiresAfterMS + TTL_BUFFER_MS;
                SetArgs setArgs = SetArgs.Builder.px(redisTTL);
                
                return redisCommands.set(key, lockValue, setArgs).thenApply(result -> {
                    if ("OK".equals(result)) {
                        acquireFuture.complete(true);
                        scheduleExpirationRemoval();
                        return true;
                    }
                    return false;
                });
            }
            return CompletableFuture.completedFuture(false);
        }, executor).thenAcceptAsync(passwordAcquired -> {
            if (passwordAcquired) {
                return;
            }
            
            // Standard lock acquisition (no password)
            long redisTTL = expiresAfterMS + TTL_BUFFER_MS;
            
            SetArgs setArgs = SetArgs.Builder.nx().px(redisTTL);
            redisCommands.set(key, lockValue, setArgs).thenAcceptAsync((result) -> {
                if ("OK".equals(result)) {
                    scheduleExpirationRemoval();
                    acquireFuture.complete(true);
                } else {
                    _retryingLocks.put(key, this);
                    // Don't complete the future yet, it will be completed on retry or timeout
                }
            });
            
        }, executor);
    }
}