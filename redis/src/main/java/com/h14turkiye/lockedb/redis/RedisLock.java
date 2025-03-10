package com.h14turkiye.lockedb.redis;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import com.h14turkiye.lockedb.ALock;
import io.lettuce.core.RedisClient;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.ScriptOutputType;

public class RedisLock extends ALock {
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, new ThreadFactory() {
        @Override
        public Thread newThread(final Runnable r) {
            final Thread thread = new Thread(r);
            thread.setDaemon(true);  // Ensures it does not block JVM shutdown
            return thread;
        }
    });
    
    // TTL buffer to ensure our release() runs before Redis auto-expires the key
    private static final long TTL_BUFFER_MS = 5;
    
    // Use a ConcurrentHashMap for thread-safe operations and faster lookups
    public static ConcurrentMap<String, RedisLock> _retryingLocks = new ConcurrentHashMap<>();
    
    // Redis channel for lock events
    private static final String LOCK_CHANNEL = "lock_events";
    
    public static void _startWatch(final RedisClient redisClient) {
        StatefulRedisPubSubConnection<String, String> pubSubConnection = redisClient.connectPubSub();
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
    
    private final RedisAsyncCommands<String, String> redisCommands;
    private CompletableFuture<Boolean> acquireFuture;
    
    private final UUID uuid = UUID.randomUUID();
    
    public RedisLock(final RedisClient redisClient, final String key) {
        this.redisCommands = redisClient.connect().async();
        this.key = key;
    }
    
    public CompletableFuture<Boolean> release() {
        acquireFuture.complete(null);
        try {
            return CompletableFuture.supplyAsync(() -> {
                String lockValue = uuid.toString() + (password != null ? ":" + password : "");
                String script = 
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "  redis.call('del', KEYS[1]) " +
                "  redis.call('publish', KEYS[2], 'DELETE:' .. KEYS[1]) " +
                "  return 1 " +
                "else " +
                "  return 0 " +
                "end";
                
                // Fix type issue - cast to Long
                Long result = (Long) redisCommands.eval(
                script, 
                ScriptOutputType.INTEGER,
                new String[] { key, LOCK_CHANNEL },
                lockValue
                ).toCompletableFuture().join();
                
                return result == 1L;
            });
        } catch (final Exception e) {
            e.printStackTrace();
        }
        return CompletableFuture.completedFuture(false);
    }
    
    public CompletableFuture<Boolean> isLocked() {
        return CompletableFuture.supplyAsync(() -> {
            String value = redisCommands.get(key).toCompletableFuture().join();
            return value != null;
        });
    }
    
    public CompletableFuture<Boolean> isAcquirable() {
        return CompletableFuture.supplyAsync(() -> {
            String value = redisCommands.get(key).toCompletableFuture().join();
            
            if (value == null) {
                return true; // Lock doesn't exist
            }
            
            // Check if it's our lock with our password
            if (password != null && value.endsWith(":" + password)) {
                return true;
            }
            
            // Check TTL to see if it's expired
            Long ttl = redisCommands.ttl(key).toCompletableFuture().join();
            return ttl <= 0; // Expired or no TTL set
        });
    }
    
    public CompletableFuture<Boolean> acquire() {
        acquireFuture = new CompletableFuture<>();
        attemptLockAcquisition();
        return acquireFuture;
    }
    
    private CompletableFuture<Void> attemptLockAcquisition() {
        return CompletableFuture.runAsync(() -> {
            if (acquireFuture.isDone()) {
                // If the future is already completed, don't attempt again
                return;
            }
            
            // Set up the timeout if needed
            if (timeoutMS > 0) {
                scheduler.schedule(() -> acquireFuture.complete(false), timeoutMS, TimeUnit.MILLISECONDS);
            }
            
            String lockValue = uuid.toString() + (password != null ? ":" + password : "");
            
            // For password protected locks
            if (password != null) {
                this.isAcquirable().thenAcceptAsync((bool) -> {
                    if (bool) {
                        long redisTTL = expiresAfterMS + TTL_BUFFER_MS;
                        
                        SetArgs setArgs = SetArgs.Builder.px(redisTTL);
                        redisCommands.set(key, lockValue, setArgs).thenAcceptAsync((result) -> {
                            if ("OK".equals(result)) {
                                acquireFuture.complete(true);
                                scheduleExpirationRemoval();
                            }
                        });
                    }
                });
                
                if (acquireFuture.isDone()) {
                    // Lock acquired via password match.
                    return;
                }
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
        });
    }
    
    private void scheduleExpirationRemoval() {
        // Schedule a task to remove the document if expiration exists
        if (expiresAfterMS > 0) {
            scheduler.schedule(this::release, expiresAfterMS, TimeUnit.MILLISECONDS);
        }
    }
}