package com.kimsooin77.sync.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "sync_job")
public class SyncJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SyncJobStatus status;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "inserted_count", nullable = false)
    private int insertedCount;

    @Column(name = "updated_count", nullable = false)
    private int updatedCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected SyncJob() {
    }

    public SyncJob(int totalCount) {
        if (totalCount < 0) {
            throw new IllegalArgumentException("totalCount must not be negative");
        }
        this.status = SyncJobStatus.RUNNING;
        this.totalCount = totalCount;
        this.startedAt = Instant.now();
    }

    void complete(int insertedCount, int updatedCount, int skippedCount, int failedCount) {
        ensureRunning();
        setCounts(insertedCount, updatedCount, skippedCount, failedCount);
        if (sumCounts() != totalCount) {
            throw new IllegalStateException("completed item count must equal totalCount");
        }
        status = failedCount == 0 ? SyncJobStatus.COMPLETED : SyncJobStatus.COMPLETED_WITH_ERRORS;
        finishedAt = Instant.now();
    }

    void fail(int insertedCount, int updatedCount, int skippedCount, int failedCount) {
        ensureRunning();
        setCounts(insertedCount, updatedCount, skippedCount, failedCount);
        status = SyncJobStatus.FAILED;
        finishedAt = Instant.now();
    }

    private void ensureRunning() {
        if (status != SyncJobStatus.RUNNING) {
            throw new IllegalStateException("only a running sync job can be finished");
        }
    }

    private void setCounts(int insertedCount, int updatedCount, int skippedCount, int failedCount) {
        this.insertedCount = requireNonNegative(insertedCount, "insertedCount");
        this.updatedCount = requireNonNegative(updatedCount, "updatedCount");
        this.skippedCount = requireNonNegative(skippedCount, "skippedCount");
        this.failedCount = requireNonNegative(failedCount, "failedCount");
    }

    private int sumCounts() {
        return Math.addExact(Math.addExact(insertedCount, updatedCount), Math.addExact(skippedCount, failedCount));
    }

    private static int requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return value;
    }

    public Long getId() {
        return id;
    }

    public SyncJobStatus getStatus() {
        return status;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getInsertedCount() {
        return insertedCount;
    }

    public int getUpdatedCount() {
        return updatedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
