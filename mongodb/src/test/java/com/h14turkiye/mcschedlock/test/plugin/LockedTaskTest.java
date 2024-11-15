package com.h14turkiye.mcschedlock.test.plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.scheduler.BukkitSchedulerMock;

import com.h14turkiye.mcschedlock.lock.AsyncLock;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class LockedTaskTest {

    private ServerMock server;
    private TestPlugin plugin;
    private BukkitSchedulerMock scheduler;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(TestPlugin.class);
        scheduler = server.getScheduler();
       AsyncLock.of("test", "start").acquire();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // MockBukkit implementation is not enough to test this
    @Test
    void testLockedTaskBehavior() {
        AtomicLong firstLockAcquiredTime = new AtomicLong();
        AtomicLong secondLockAcquiredTime = new AtomicLong();
        AtomicBoolean secondLockAcquired = new AtomicBoolean(false);
        AtomicBoolean firstLockAcquired = new AtomicBoolean(false);
        AtomicBoolean thirdLockAcquired = new AtomicBoolean(false);

        // Simulate the first lock
        AsyncLock firstLock = AsyncLock.of("lockedTask", "bypassToken");
        
        firstLock.onLockAcquiredSync(() -> {
            firstLockAcquiredTime.set(System.currentTimeMillis());
            firstLockAcquired.set(true);
        }).releaseAfterTask(false).acquire();

        // Schedule the second lock with the same bypass token
        scheduler.runTaskLater(plugin, () -> {
            AsyncLock secondLock = AsyncLock.of("lockedTask", "bypassToken");
            secondLock.onLockAcquiredSync(() -> {
                secondLockAcquiredTime.set(System.currentTimeMillis());
                secondLockAcquired.set(true);
            }).acquire();
        }, 2);

        scheduler.runTaskLater(plugin, () -> {
            AsyncLock thirdLock = AsyncLock.of("lockedTask");
            thirdLock.onLockAcquiredSync(() -> {
               thirdLockAcquired.set(true);
            }).releaseAfterTask(false).maxRetry(0).acquire();
        }, 1);

        // Advance the scheduler to simulate time passage
        scheduler.performTicks(20);

        // Validate the behavior
        assertTrue(firstLockAcquired.get(), "First lock should have been acquired.");
        assertTrue(secondLockAcquired.get(), "Second lock should have been acquired with bypassToken.");
        assertTrue(!thirdLockAcquired.get(), "Third lock should not have been acquired since first lock is active.");
        long delay = secondLockAcquiredTime.get() - firstLockAcquiredTime.get();
        assertTrue(delay < 150L, "Second lock acquisition should have been near-instant.");
    }
}