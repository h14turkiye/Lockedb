package com.h14turkiye.mcschedlock.lock;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.bukkit.plugin.Plugin;

import com.tcoded.folialib.impl.PlatformScheduler;

/**
 * Represents an abstract asynchronous lock for managing access to shared resources in a Minecraft plugin.
 * This class provides methods for creating and configuring locks, handling events related to lock acquisition,
 * and managing task retries and expiration.
 */
public abstract class AsyncLock {
    protected static final Runnable EMPTY_RUNNABLE = () -> {};

    protected final String key;
    protected final String bypassToken;

    protected static Plugin plugin;
    protected static PlatformScheduler scheduler;

    protected Runnable onLockAcquiredSyncRunnable = EMPTY_RUNNABLE;
    protected Runnable onLockAcquiredAsyncRunnable = EMPTY_RUNNABLE;
    protected Runnable onLockTryAcquireFailedSyncRunnable = EMPTY_RUNNABLE;
    protected Runnable onLockTryAcquireFailedAsyncRunnable = EMPTY_RUNNABLE;
    protected byte onLockTaskCompletedAmount = 0;

    protected int maxRetry = -1; // Will not cancel the task if it is -1
    protected int retryCount = 0;

    protected long intervalMS = 50L;
    protected long expiresAfterMS = TimeUnit.DAYS.toMillis(1);

    /**
     * Constructs an AsyncLock instance with a specified key.
     *
     * @param key the unique key for the lock
     */
    protected AsyncLock(String key) {
        this.bypassToken = null;
        this.key = key.isEmpty() ? null : key;
        if (plugin == null) {
            plugin = LockConnect.plugin;
        }
    }

    /**
     * Constructs an AsyncLock instance with a specified key and bypass token.
     *
     * @param key         the unique key for the lock
     * @param bypassToken the token used to bypass the lock
     */
    protected AsyncLock(String key, String bypassToken) {
        this.key = key.isEmpty() ? null : key;
        this.bypassToken = bypassToken.isEmpty() ? null : bypassToken;
        if (plugin == null) {
            plugin = LockConnect.plugin;
        }
    }

    /**
     * Creates an instance of AsyncLock using the class specified in the plugin's configuration.
     * The class must have a constructor that accepts a single String parameter.
     *
     * @param key the key to use for the lock
     * @return an instance of AsyncLock
     * @throws IllegalArgumentException if the constructor is not found or cannot be accessed
     */
    public static AsyncLock of(String key) {
        Class<? extends AsyncLock> type = LockConnect.asyncLockType;
        try {
            return type.getConstructor(String.class).newInstance(key);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                | NoSuchMethodException | SecurityException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Creates an instance of AsyncLock using the class specified in the plugin's configuration.
     * The class must have a constructor that accepts two String parameters.
     *
     * @param key         the key to use for the lock
     * @param bypassToken the token used to bypass the lock
     * @return an instance of AsyncLock
     * @throws IllegalArgumentException if the constructor is not found or cannot be accessed
     */
    public static AsyncLock of(String key, String bypassToken) {
        Class<? extends AsyncLock> type = LockConnect.asyncLockType;
        try {
            return type.getConstructor(String.class, String.class).newInstance(key, bypassToken);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                | NoSuchMethodException | SecurityException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Checks if the lock can be acquired using the specified bypass token.
     *
     * @return a CompletableFuture that resolves to true if the lock can be acquired, false otherwise
     */
    public abstract CompletableFuture<Boolean> check();

    /**
     * Acquires the lock asynchronously.
     *
     * @return an instance of AsyncLock for chaining
     */
    public abstract AsyncLock acquire();

    /**
     * Releases the lock.
     */
    public abstract void release();

    /**
     * Checks if the lock exists.
     *
     * @return a CompletableFuture that resolves to true if the lock exists, false otherwise
     */
    public abstract CompletableFuture<Boolean> exist();

    /**
     * Sets a synchronous runnable to be executed when the lock is acquired.
     *
     * @param runnable the code to run when the lock is acquired
     * @return the current instance of AsyncLock for chaining
     */
    public AsyncLock onLockAcquiredSync(Runnable runnable) {
        this.onLockAcquiredSyncRunnable = runnable;
        return this;
    }

    /**
     * Sets an asynchronous runnable to be executed when the lock is acquired.
     *
     * @param runnable the code to run when the lock is acquired
     * @return the current instance of AsyncLock for chaining
     */
    public AsyncLock onLockAcquiredAsync(Runnable runnable) {
        this.onLockAcquiredAsyncRunnable = runnable;
        return this;
    }

    /**
     * Sets a synchronous runnable to be executed when the attempt to acquire the lock fails.
     *
     * @param runnable the code to run when lock acquisition fails
     * @return the current instance of AsyncLock for chaining
     */
    public AsyncLock onLockTryAcquireFailedSync(Runnable runnable) {
        this.onLockTryAcquireFailedSyncRunnable = runnable;
        return this;
    }

    /**
     * Sets an asynchronous runnable to be executed when the attempt to acquire the lock fails.
     *
     * @param runnable the code to run when lock acquisition fails
     * @return the current instance of AsyncLock for chaining
     */
    public AsyncLock onLockTryAcquireFailedAsync(Runnable runnable) {
        this.onLockTryAcquireFailedAsyncRunnable = runnable;
        return this;
    }

    /**
     * Sets the maximum number of retries for acquiring the lock.
     * Default value is -1, meaning no limit on retries.
     *
     * @param maxRetry the maximum number of retries. If -1, the task will not be cancelled after a failure.
     *                 If 0, the task will be cancelled after the first failure.
     * @return the current instance of AsyncLock for chaining
     */
    public AsyncLock maxRetry(int maxRetry) {
        this.maxRetry = maxRetry;
        return this;
    }

    /**
     * Sets the interval between retries in milliseconds.
     *
     * @param intervalms the interval in milliseconds
     * @return the current instance of AsyncLock for chaining
     */
    public AsyncLock interval(Long intervalms) {
        this.intervalMS = intervalms;
        return this;
    }

    /**
     * Sets the expiration time for the lock in milliseconds.
     *
     * @param expirems the expiration time in milliseconds
     * @return the current instance of AsyncLock for chaining
     */
    public AsyncLock expires(Long expirems) {
        this.expiresAfterMS = expirems;
        return this;
    }

    /**
     * Sets whether the lock should be released automatically after the associated task completes.
     *
     * @param release true to release after the task, false otherwise
     * @return the current instance of AsyncLock for chaining
     */
    public AsyncLock releaseAfterTask(boolean release) {
        this.onLockTaskCompletedAmount = (byte) (release ? 0 : -1);
        return this;
    }

    /**
     * Thing you need to use.
     *     protected static final Runnable EMPTY_RUNNABLE = () -> {};

            protected final String key;
            protected final String bypassToken;

            protected static Plugin plugin;
            protected static PlatformScheduler scheduler;

            protected Runnable onLockAcquiredSyncRunnable = EMPTY_RUNNABLE;
            protected Runnable onLockAcquiredAsyncRunnable = EMPTY_RUNNABLE;
            protected Runnable onLockTryAcquireFailedSyncRunnable = EMPTY_RUNNABLE;
            protected Runnable onLockTryAcquireFailedAsyncRunnable = EMPTY_RUNNABLE;
            protected byte onLockTaskCompletedAmount = 0;

            protected int maxRetry = -1; // Will not cancel the task if it is -1
            protected int retryCount = 0;

            protected long intervalMS = 50L;
            protected long expiresAfterMS = TimeUnit.DAYS.toMillis(1);
            
            protected AsyncLock(String key);
            protected AsyncLock(String key, String bypassToken);

     */
    protected abstract boolean I_IMPLEMENTED_CONSTRUCTORS_AND_CONFIGURATIONS();
}
