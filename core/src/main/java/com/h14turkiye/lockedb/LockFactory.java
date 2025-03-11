package com.h14turkiye.lockedb;

import java.util.concurrent.CompletableFuture;

public interface LockFactory {
    // Builder pattern approach
    public ALockBuilder builder();

    // Direct creation approach
    public ALock createLock(String key);

    public CompletableFuture<String> getPassword(String key);
}
