package com.kimsooin77.sync.api.sync;
import com.kimsooin77.sync.sync.SyncJob;
import com.kimsooin77.sync.sync.SyncJobStatus;
import java.time.Instant;
public record SyncJobListResponse(Long syncJobId, SyncJobStatus status, int totalCount, int insertedCount,
        int updatedCount, int skippedCount, int failedCount, String failureCode, Instant startedAt, Instant finishedAt) {
    public static SyncJobListResponse from(SyncJob job) {
        return new SyncJobListResponse(job.getId(), job.getStatus(), job.getTotalCount(), job.getInsertedCount(),
                job.getUpdatedCount(), job.getSkippedCount(), job.getFailedCount(), job.getFailureCode(),
                job.getStartedAt(), job.getFinishedAt());
    }
}
