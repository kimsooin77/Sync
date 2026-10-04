package com.kimsooin77.sync.sync;

import java.time.Instant;

public record SyncJobResult(
        Long id,
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

    static SyncJobResult from(SyncJob job) {
        return new SyncJobResult(
                job.getId(),
                job.getStatus(),
                job.getTotalCount(),
                job.getInsertedCount(),
                job.getUpdatedCount(),
                job.getSkippedCount(),
                job.getFailedCount(),
                job.getFailureCode(),
                job.getFailureMessage(),
                job.getStartedAt(),
                job.getFinishedAt());
    }
}
