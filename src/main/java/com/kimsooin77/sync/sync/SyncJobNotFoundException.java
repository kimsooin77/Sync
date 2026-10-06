package com.kimsooin77.sync.sync;
public class SyncJobNotFoundException extends RuntimeException {
    public SyncJobNotFoundException(Long id) { super("Sync job was not found: " + id); }
}
