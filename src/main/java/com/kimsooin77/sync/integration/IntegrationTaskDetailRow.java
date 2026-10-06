package com.kimsooin77.sync.integration;
import java.time.Instant;
import java.util.UUID;
public record IntegrationTaskDetailRow(Long id, String employeeNo, IntegrationTarget target, IntegrationAction action,
        IntegrationTaskStatus status, String payload, UUID idempotencyKey, int retryCount, int maxRetryCount,
        Instant nextRetryAt, Instant processingStartedAt, String lastErrorCode, String lastErrorMessage,
        Instant createdAt, Instant updatedAt) { }
