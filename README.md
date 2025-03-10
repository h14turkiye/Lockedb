# Lockedb

**Lockedb** is a robust, customizable lock management system designed for managing distributed locks across various databases, primarily intended for use with Minecraft plugins.

## Table of Contents

- [Overview](#overview)
- [System Requirements](#system-requirements)
  - [Threading](#threading)
- [Installation](#installation)
- [Setup](#setup)
- [Usage](#usage)
  - [Creating and Configuring Locks](#creating-and-configuring-locks)
  - [Acquiring and Releasing Locks](#acquiring-and-releasing-locks)
  - [Checking Lock Status](#checking-lock-status)
- [Supported Databases](#supported-databases)
- [Configuration Options](#configuration-options)
  - [Password Protection](#password-protection)
- [Examples](#examples)

## Overview

Lockedb provides a simple yet powerful API for distributed locking, allowing you to synchronize access to resources across multiple servers or instances. It features:

- Asynchronous operations with CompletableFuture
- Configurable timeouts and expiration
- Optional password protection
- Support for multiple database backends

## System Requirements

### Threading

Lockedb uses a background thread pool for managing lock timeouts and expirations. The library requires at least 2 threads in its internal scheduled executor service.

```java
private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, new ThreadFactory() { // Mainly used for scheduling and event listening
    @Override
    public Thread newThread(final Runnable r) {
        final Thread thread = new Thread(r);
        thread.setDaemon(true); // Ensures it does not block JVM shutdown
        return thread;
    }
});
```

## Installation

Add Lockedb with your preferred database to your project's dependencies. For example, with MongoDB:

```gradle
repositories {
    maven { url = 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.h14turkiye:lockedb-mongodb:main-SNAPSHOT'
    
    // Include MongoDB dependencies
    implementation 'org.mongodb:bson:4.9.1'
    implementation 'org.mongodb:mongo-java-driver:3.12.10'
}
```

To avoid dependency conflicts, use the Shadow plugin to relocate dependencies:

```gradle
shadowJar {
    relocate 'com.h14turkiye.lockedb', 'your.org.app.relocated.lockedb'
    // Preferably relocate MongoDB and BSON as well
    relocate 'com.mongodb', 'your.org.app.relocated.mongodb'
    relocate 'org.bson', 'your.org.app.relocated.bson'
}
```

## Setup

Initialize the lock builder for your database. Currently, MongoDB is supported:

```java
// Get your database connection
MongoDatabase db = ...;

// Create a lock factory
LockFactory factory = new MongoLockFactory(db);
```

## Usage

### Creating and Configuring Locks

Create and configure a lock using the builder pattern:

```java
// Create a lock with custom configuration
ALock lock = factory.builder()
    .expiresAfterMS(60000L)        // Lock expires after 1 minute
    .timeoutMS(5000L)              // Wait up to 5 seconds to acquire
    .password("optional-secret")   // Optional password protection
    .build("resource-key");        // Unique resource identifier
```

Or with a different approach:

```java
ALock lock = factory.createLock("resource-key");
lock.setExpiresAfterMS(60000L);
lock.setTimeoutMS(5000L);
lock.setPassword("optional-secret");
```

### Acquiring and Releasing Locks

Use the lock asynchronously with CompletableFuture:

```java
// Acquire the lock asynchronously
lock.acquire().thenAccept(success -> {
    if (success) {
        System.out.println("Lock acquired!");
        // Perform operations requiring the lock
        
        // Release the lock when done
        lock.release().thenAccept(released -> {
            if (released) {
                System.out.println("Lock released successfully");
            }
        });
    } else {
        System.out.println("Failed to acquire lock");
    }
});
```

### Checking Lock Status

Check if a resource is currently locked or available:

```java
// Check if the resource is currently locked
lock.isLocked().thenAccept(locked -> {
    System.out.println("Resource is " + (locked ? "locked" : "available"));
});

// Check if the lock can be acquired
lock.isAcquirable().thenAccept(acquirable -> {
    System.out.println("Lock is " + (acquirable ? "acquirable" : "not acquirable"));
});
```

## Supported Databases

Currently supported databases:

- MongoDB
- Redis/Dragonfly

## Configuration Options

| Option | Description | Default |
|--------|-------------|---------|
| `expiresAfterMS` | Time after which the lock automatically expires | 24 hours |
| `timeoutMS` | Maximum time to wait when acquiring a lock | 30 seconds |
| `password` | Optional password for lock protection | None |

### Password Protection

The password parameter is optional - if not set, no password will be used for authentication. When a password is provided, it enables a special feature: **a lock that is already acquired can be re-acquired by another process if the correct password is provided**.

This is useful in scenarios where:

- You need to transfer lock ownership between processes
- A specific admin process needs to override existing locks
- You want to implement hierarchical locking with master/slave processes

Example of using password protection:

```java
// Process 1: Create and acquire a password-protected lock
ALock lockWithPassword = builder
    .password("master-key")
    .build("protected-resource");

lockWithPassword.acquire().thenAccept(acquired -> {
    System.out.println("Process 1: Lock acquired: " + acquired);
});

// Process 2: Can acquire the same lock if it knows the password
ALock sameResourceLock = builder
    .password("master-key")  // Must match the password from Process 1
    .build("protected-resource");

sameResourceLock.acquire().thenAccept(acquired -> {
    // This will succeed if the password matches, even though
    // Process 1 already holds the lock
    System.out.println("Process 2: Lock acquired: " + acquired);
});

// Process 3: Cannot acquire the lock without the correct password
ALock lockWithWrongPassword = builder
    .password("wrong-key")
    .build("protected-resource");

lockWithWrongPassword.acquire().thenAccept(acquired -> {
    // This will fail because the password doesn't match
    System.out.println("Process 3: Lock acquired: " + acquired);
});
```

## Examples

Complete example with error handling:

```java
// Create a lock
ALock lock = builder
    .expiresAfterMS(120000L)
    .timeoutMS(10000L)
    .build("player-inventory");

// Try to acquire the lock
lock.acquire()
    .thenCompose(acquired -> {
        if (!acquired) {
            System.out.println("Could not acquire lock - resource busy");
            return CompletableFuture.completedFuture(false);
        }
        
        try {
            // Perform critical operation here
            System.out.println("Lock acquired, performing operation...");
            
            // Simulate work
            Thread.sleep(1000);
            
            return lock.release();
        } catch (Exception e) {
            e.printStackTrace();
            return lock.release();
        }
    })
    .exceptionally(ex -> {
        ex.printStackTrace();
        return false;
    });
```
