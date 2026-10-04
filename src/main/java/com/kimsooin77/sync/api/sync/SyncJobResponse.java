package com.kimsooin77.sync.api.sync;

import com.kimsooin77.sync.sync.SyncJobResult;
import com.kimsooin77.sync.sync.SyncJobStatus;

import java.time.Instant;

public record SyncJobResponse(
        Long syncJobId,
        SyncJobStatus status,
        int totalCount,
        int insertedCount,
        int updatedCount,
        int skippedCount,
        int failedCount,
        String failureCode,
        String failureMessage,
        Instant startedAt,
        Instant finishedAt
) {

    public static SyncJobResponse from(SyncJobResult result) {
        return new SyncJobResponse(
                result.id(),
                result.status(),
                result.totalCount(),
                result.insertedCount(),
                result.updatedCount(),
                result.skippedCount(),
                result.failedCount(),
                result.failureCode(),
                result.failureMessage(),
                result.startedAt(),
                result.finishedAt());
    }
}
