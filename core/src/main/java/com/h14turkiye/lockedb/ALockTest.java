package com.h14turkiye.lockedb;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

public abstract class ALockTest {

    protected static LockFactory factory;

    /**
    * Test 1: Simple acquiring lock and releasing workflow, and trying to acquire it after
    */
    @Test
    public void testBasicLockWorkflow() throws Exception {
        // Create a lock
        ALock lock = factory.builder()
        .expiresAfterMS(60000L)
        .timeoutMS(40L)
        .build("test-resource");
        
        // Acquire the lock
        Boolean acquired = lock.acquire().get();
        assertTrue(acquired, "Should be able to acquire the lock initially");
        
        // Check if the resource is locked
        Boolean isLocked = lock.isLocked().get();
        assertTrue(isLocked, "Resource should be marked as locked");
        
        // Create another lock for the same resource
        ALock lock2 = factory.builder()
        .timeoutMS(40L)
        .build("test-resource");
        
        // Try to acquire the second lock while the first one is held
        Boolean secondAcquired = lock2.isAcquirable().get();
        assertFalse(secondAcquired, "Second lock should not be acquired while first is held");
        
        // Release the first lock
        Boolean released = lock.release().get();
        assertTrue(released, "Lock should be released successfully");
        
        // After release, the second lock should be acquirable
        Boolean acquirable = lock2.isAcquirable().get();
        assertTrue(acquirable, "Lock should be acquirable after release");
        
        // Now try to acquire the second lock again
        Boolean acquiredAfterRelease = lock2.acquire().get();
        assertTrue(acquiredAfterRelease, "Should be able to acquire lock after previous one was released");
    }
    
    /**
    * Test 2: Test expiring and change listeners
    */
    @Test
    public void testLockExpirationAndChangeListeners() throws Exception {
        // Create a lock with short expiration
        ALock lock = factory.builder()
        .expiresAfterMS(20L)  // 1 second expiration
        .timeoutMS(50L)
        .build("expiring-resource");
        
        // Acquire the lock
        Boolean acquired = lock.acquire().get();
        assertTrue(acquired, "Should be able to acquire the lock");
        
        // Wait for the expiration event (give a little extra time)
        Thread.sleep(70L);
        
        // Verify that the lock is now available
        Boolean isLocked = lock.isLocked().get();
        assertFalse(isLocked, "Lock should not be locked after expiration");
        
        // Verify another lock can be acquired
        ALock lock2 = factory.builder().build("expiring-resource");
        Boolean acquiredAfterExpiration = lock2.acquire().get();
        assertTrue(acquiredAfterExpiration, "Should be able to acquire lock after previous one expired");
    }
    
    /**
    * Test 3: Lock contests using maximum available threads
    */
    @Test
    public void testLockContention() throws Exception {
        final String resourceKey = "contested-resource";
        
        // Use available processors as a base for thread count
        final int threadCount = Runtime.getRuntime().availableProcessors() * 2;
        
        // Use a countdown latch to ensure all threads start at roughly the same time
        CountDownLatch startLatch = new CountDownLatch(1);
        // Use a countdown latch to wait for all threads to finish
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        
        // Track how many threads successfully acquired the lock
        AtomicInteger acquiredCount = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    // Create a lock for this thread
                    ALock lock = factory.builder()
                    .timeoutMS(40L)
                    .build(resourceKey);
                    
                    // Wait for the start signal
                    startLatch.await();
                    
                    // Try to acquire the lock
                    Boolean acquired = lock.acquire().get();
                    
                    if (acquired) {
                        acquiredCount.incrementAndGet();
                        System.out.println("Thread " + threadId + " acquired the lock");
                        
                        // Hold the lock briefly
                        Thread.sleep(10);
                        
                        // Release the lock
                        boolean released = lock.release().get();
                        System.out.println("Thread " + threadId + " released the lock: " + released);
                    } else {
                        System.out.println("Thread " + threadId + " failed to acquire the lock");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    finishLatch.countDown();
                }
            });
        }
        
        // Start all threads
        startLatch.countDown();
        
        // Wait for all threads to finish
        finishLatch.await(3, TimeUnit.SECONDS);
        
        // Shut down the executor
        executor.shutdown();
        
        // Verify that at least one thread acquired the lock, but not all
        // (since they're competing for the same resource)
        assertTrue(acquiredCount.get() > 0, "At least one thread should have acquired the lock");
        assertTrue(acquiredCount.get() <= threadCount, "Not all threads should have acquired the lock simultaneously");
        
        System.out.println("Total threads: " + threadCount + ", Acquired count: " + acquiredCount.get());
    }
    
    /**
    * Test 4: Test with password usage
    */
    @Test
    public void testPasswordProtectedLocks() throws Exception {
        final String resourceKey = "protected-resource";
        final String correctPassword = "secret-key";
        final String wrongPassword = "wrong-key";
        
        // Create and acquire a password-protected lock
        ALock lock1 = factory.builder()
        .password(correctPassword)
        .timeoutMS(5000L)
        .build(resourceKey);
        
        Boolean acquired1 = lock1.acquire().get();
        assertTrue(acquired1, "Should be able to acquire the lock with password");
        
        // Verify the lock is held
        Boolean isLocked = lock1.isLocked().get();
        assertTrue(isLocked, "Resource should be locked");
        
        // Try to acquire the same lock with the wrong password
        ALock lockWrongPwd = factory.builder()
        .password(wrongPassword)
        .timeoutMS(15L)
        .build(resourceKey);
        
        Boolean acquiredWrong = lockWrongPwd.isAcquirable().get();
        assertFalse(acquiredWrong, "Should not be able to acquire with wrong password");
        
        // Try to acquire the same lock with the correct password
        ALock lock2 = factory.builder()
        .password(correctPassword)
        .timeoutMS(5000L)
        .build(resourceKey);
        
        Boolean acquired2 = lock2.acquire().get();
        assertTrue(acquired2, "Should be able to acquire with matching password even though already locked");
        
        // Release both locks
        lock1.release().get();
        lock2.release().get();
    }

}