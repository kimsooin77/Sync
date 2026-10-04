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

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected SyncJob() {
        this(0);
    }

    public SyncJob(int totalCount) {
        if (totalCount < 0) {
            throw new IllegalArgumentException("totalCount must not be negative");
        }
        this.status = SyncJobStatus.RUNNING;
        this.totalCount = totalCount;
        this.startedAt = Instant.now();
    }

    void setTotalCount(int totalCount) {
        ensureRunning();
        if (totalCount < 0) {
            throw new IllegalArgumentException("totalCount must not be negative");
        }
        if (insertedCount + updatedCount + skippedCount + failedCount != 0) {
            throw new IllegalStateException("cannot change totalCount after employee processing has started");
        }
        this.totalCount = totalCount;
    }

    void complete(int insertedCount, int updatedCount, int skippedCount, int failedCount) {
        ensureRunning();
        setCounts(insertedCount, updatedCount, skippedCount, failedCount);
        if (sumCounts() != totalCount) {
            throw new IllegalStateException("completed item count must equal totalCount");
        }
        status = failedCount == 0 ? SyncJobStatus.COMPLETED : SyncJobStatus.COMPLETED_WITH_ERRORS;
        failureCode = null;
        failureMessage = null;
        finishedAt = Instant.now();
    }

    void fail(
            int insertedCount,
            int updatedCount,
            int skippedCount,
            int failedCount,
            String failureCode,
            String failureMessage
    ) {
        ensureRunning();
        setCounts(insertedCount, updatedCount, skippedCount, failedCount);
        status = SyncJobStatus.FAILED;
        this.failureCode = requireText(failureCode, "failureCode", 64);
        this.failureMessage = requireText(failureMessage, "failureMessage", 500);
        finishedAt = Instant.now();
    }

    private static String requireText(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        String normalized = value.strip();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
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

    public String getFailureCode() {
        return failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
