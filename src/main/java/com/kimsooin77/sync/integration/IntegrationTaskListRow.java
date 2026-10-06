package com.kimsooin77.sync.integration;
import java.time.Instant;
public record IntegrationTaskListRow(Long id, String employeeNo, IntegrationAction action, IntegrationTaskStatus status,
                                     int retryCount, int maxRetryCount, String lastErrorCode, Instant createdAt, Instant updatedAt) { }
