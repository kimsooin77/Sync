package com.kimsooin77.sync.integration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "integration_attempt", uniqueConstraints = @UniqueConstraint(
        name = "uk_integration_attempt_task_attempt_no", columnNames = {"integration_task_id", "attempt_no"}))
public class IntegrationAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "integration_task_id", nullable = false)
    private IntegrationTask integrationTask;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IntegrationAttemptResult result;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "error_code", columnDefinition = "text")
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IntegrationAttempt() {
    }

    private IntegrationAttempt(IntegrationTask integrationTask, int attemptNo, Instant startedAt, Instant finishedAt,
                               IntegrationAttemptResult result, Integer httpStatus, String errorCode,
                               String errorMessage) {
        this.integrationTask = Objects.requireNonNull(integrationTask, "integrationTask");
        if (attemptNo < 1) {
            throw new IllegalArgumentException("attemptNo must be positive");
        }
        this.attemptNo = attemptNo;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.finishedAt = Objects.requireNonNull(finishedAt, "finishedAt");
        if (finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("finishedAt must not precede startedAt");
        }
        this.result = Objects.requireNonNull(result, "result");
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.createdAt = finishedAt;
    }

    public static IntegrationAttempt succeeded(IntegrationTask task, int attemptNo, Instant startedAt,
                                               Instant finishedAt, int httpStatus) {
        return new IntegrationAttempt(task, attemptNo, startedAt, finishedAt,
                IntegrationAttemptResult.SUCCESS, httpStatus, null, null);
    }

    public static IntegrationAttempt failed(IntegrationTask task, int attemptNo, Instant startedAt,
                                            Instant finishedAt, Integer httpStatus, String errorCode,
                                            String errorMessage) {
        return new IntegrationAttempt(task, attemptNo, startedAt, finishedAt,
                IntegrationAttemptResult.FAILED, httpStatus, Objects.requireNonNull(errorCode, "errorCode"),
                Objects.requireNonNull(errorMessage, "errorMessage"));
    }

    public Long getId() { return id; }
    public Long getIntegrationTaskId() { return integrationTask.getId(); }
    public int getAttemptNo() { return attemptNo; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public IntegrationAttemptResult getResult() { return result; }
    public Integer getHttpStatus() { return httpStatus; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
}
