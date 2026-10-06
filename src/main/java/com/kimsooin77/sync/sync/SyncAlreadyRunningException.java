package com.kimsooin77.sync.sync;

public class SyncAlreadyRunningException extends RuntimeException {
    public SyncAlreadyRunningException() {
        super("A sync job is already running.");
    }
}
