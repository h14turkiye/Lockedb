package com.h14turkiye.lockedb;

public interface LockFactory {
    // Builder pattern approach
    public ALockBuilder builder();

    // Direct creation approach
    public ALock createLock(String key);
}
