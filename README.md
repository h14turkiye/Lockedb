# McSchedLock - Minecraft Lock Plugin

**McSchedLock** is a robust, customizable lock management system for handling tasks in Minecraft plugins. This plugin allows flexible task handling, complete with retries, expiration settings, and optional lock bypass mechanisms.

## Table of Contents

- [Installation](#installation)
- [Setup](#setup)
- [Usage](#usage)
  - [Creating Locks](#creating-locks)
  - [Configuring Locks](#configuring-locks)
  - [Handling Asynchronous Events](#handling-asynchronous-events)
- [Examples](#examples)

## Installation

1. Add AsyncLock to your project's dependencies:

   ```gradle
   repositories {
       maven { url = 'https://jitpack.io' }
   }

   dependencies {
       implementation 'com.github.h14turkiye:McSchedLock:main-SNAPSHOT'
   }
   ```

2. Use the Shadow plugin to relocate dependencies, avoiding conflicts:

   ```gradle
   shadowJar {
       relocate 'com.h14turkiye.mcschedlock', 'your.org.app.relocated.mcschedlock'
   }
   ```

## Setup

In the `onEnable` method of your plugin, initialize AsyncLock:

```java
@Override
public void onEnable() {
    LockConnect.connectTo...(this);
}
```

## Usage

### Creating Locks

To create a lock:

```java
AsyncLock lock = AsyncLock.of("unique-key");
AsyncLock bypassLock = AsyncLock.of("unique-key", "bypass-token"); // Optional bypass token allows authorized access when a lock is active.
```

### Configuring Locks

Configure lock behavior with chaining methods:

```java
lock
    .maxRetry(3) // Retry up to 3 times (-1 for unlimited retries, default)
    .interval(100L) // Retry interval in milliseconds (default: 50ms)
    .expires(TimeUnit.HOURS.toMillis(1)) // Expire lock after 1 hour (default: 1 day)
    .releaseAfterTask(true); // Auto-release lock upon task completion (default: true)
```

### Handling Asynchronous Events

Define synchronous and asynchronous actions to handle lock events:

```java
lock
    .onLockAcquiredSync(() -> {
        // Code to execute synchronously when lock is acquired
    })
    .onLockAcquiredAsync(() -> {
        // Code to execute asynchronously when lock is acquired
    })
    .onLockTryAcquireFailedSync(() -> {
        // Code to execute synchronously if lock acquisition fails
    })
    .onLockTryAcquireFailedAsync(() -> {
        // Code to execute asynchronously if lock acquisition fails
    });
```

## Examples

Here’s an example demonstrating how to configure and use an AsyncLock:

```java
AsyncLock lock = AsyncLock.of("resource-key")
    .onLockAcquiredSync(() -> System.out.println("Lock acquired successfully."))
    .onLockTryAcquireFailedSync(() -> System.out.println("Failed to acquire lock."))
    .maxRetry(3)
    .interval(200L)
    .expires(TimeUnit.MINUTES.toMillis(10))
    .releaseAfterTask(true);

lock.acquire();
```

In this example:

- The lock attempts acquisition with a maximum of 3 retries and a 200ms interval.
- The lock expires after 10 minutes.
- The lock releases automatically once the task completes.
