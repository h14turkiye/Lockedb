package com.h14turkiye.lockedb;

import java.util.concurrent.CompletableFuture;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents an abstract distributed lock with asynchronous operations.
 * Implementations must provide mechanisms for acquiring, releasing, and checking lock status.
 */
public abstract class ALock {
    
    /** The unique key identifying the lock. */
    @Getter @Setter protected String key;

    /**
     * This is an optional parameter. If not set, no password will be used.
     * When a password is set, an acquired lock can be re-acquired even if it is already locked,
     * provided the password matches.
     *
     * @param password the password to be used for authentication
     * @return the current instance of ALockBuilder for chaining
     */
    @Getter @Setter protected String password;

    /** The expiration time of the lock in milliseconds. */
    @Getter @Setter protected long expiresAfterMS;

    /** The timeout duration for acquiring the lock in milliseconds. */
    @Getter @Setter protected long timeoutMS;

    /**
     * Attempts to acquire the lock asynchronously.
     *
     * @return a CompletableFuture that resolves to:
     *         - {@code true} if the lock was successfully acquired.
     *         - {@code false} if the operation timed out before the lock could be acquired.
     *         - {@code null} if the lock was released before it could be acquired.
     */
    public abstract CompletableFuture<Boolean> acquire();

    /**
     * Releases the lock asynchronously.
     *
     * @return a CompletableFuture that resolves to {@code true} if the lock was successfully released, otherwise {@code false}.
     */
    public abstract CompletableFuture<Boolean> release();

    /**
     * Checks asynchronously whether the lock is currently held by any process.
     *
     * @return a CompletableFuture that resolves to {@code true} if the lock is currently held, otherwise {@code false}.
     */
    public abstract CompletableFuture<Boolean> isLocked();

    /**
     * Checks asynchronously whether the lock can be acquired.
     *
     * @return a CompletableFuture that resolves to {@code true} if the lock is acquirable, otherwise {@code false}.
     */
    public abstract CompletableFuture<Boolean> isAcquirable();
}
