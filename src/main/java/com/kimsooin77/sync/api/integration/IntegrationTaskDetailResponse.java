package com.kimsooin77.sync.api.integration;
import tools.jackson.databind.JsonNode;
import com.kimsooin77.sync.integration.IntegrationAction;
import com.kimsooin77.sync.integration.IntegrationTaskDetailRow;
import com.kimsooin77.sync.integration.IntegrationTaskStatus;
import com.kimsooin77.sync.integration.IntegrationTarget;
import java.time.Instant;
import java.util.UUID;
public record IntegrationTaskDetailResponse(Long id, String employeeNo, IntegrationTarget target, IntegrationAction action,
        IntegrationTaskStatus status, JsonNode payload, boolean payloadParseError, UUID idempotencyKey,
        int retryCount, int maxRetryCount, Instant nextRetryAt, Instant processingStartedAt, String lastErrorCode,
        String lastErrorMessage, Instant createdAt, Instant updatedAt) {
    public static IntegrationTaskDetailResponse from(IntegrationTaskDetailRow row, JsonNode parsed, boolean parseError) {
        return new IntegrationTaskDetailResponse(row.id(), row.employeeNo(), row.target(), row.action(), row.status(),
                parsed, parseError, row.idempotencyKey(), row.retryCount(), row.maxRetryCount(), row.nextRetryAt(),
                row.processingStartedAt(), row.lastErrorCode(), row.lastErrorMessage(), row.createdAt(), row.updatedAt());
    }
}
