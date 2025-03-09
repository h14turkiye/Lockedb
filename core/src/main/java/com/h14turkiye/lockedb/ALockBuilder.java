package com.h14turkiye.lockedb;

/**
 * An abstract builder class for creating locks.
 */
public abstract class ALockBuilder {
    protected String password;
    protected Long expiresAfterMS = 24 * 60 * 60 * 1000L; // Default: 24 hours
    protected Long timeoutMS = 30 * 1000L; // Default: 30 seconds

    /**
     * Builds an instance of ALock with the specified key.
     *
     * @param key the unique key for the lock
     * @return an instance of ALock
     */
    public abstract ALock build(final String key);

    /**
     * Sets the password for the lock.
     * 
     * This is an optional parameter. If not set, no password will be used.
     * When a password is set, an acquired lock can be re-acquired even if it is already locked,
     * provided the password matches.
     *
     * @param password the password to be used for authentication
     * @return the current instance of ALockBuilder for chaining
     */
    public ALockBuilder password(final String password) {
        this.password = password;
        return this;
    }

    /**
     * Sets the expiration time for the lock in milliseconds.
     *
     * @param expireMS the expiration time in milliseconds
     * @return the current instance of ALockBuilder for chaining
     */
    public ALockBuilder expiresAfterMS(final Long expireMS) {
        this.expiresAfterMS = expireMS;
        return this;
    }

    /**
     * Sets the timeout duration for acquiring the lock in milliseconds.
     *
     * @param timeoutMS the timeout duration in milliseconds
     * @return the current instance of ALockBuilder for chaining
     */
    public ALockBuilder timeoutMS(final Long timeoutMS) {
        this.timeoutMS = timeoutMS;
        return this;
    }
}
