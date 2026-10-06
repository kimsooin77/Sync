package com.kimsooin77.sync.api.integration;
import com.kimsooin77.sync.integration.IntegrationAction;
import com.kimsooin77.sync.integration.IntegrationTaskListRow;
import com.kimsooin77.sync.integration.IntegrationTaskStatus;
import java.time.Instant;
public record IntegrationTaskListResponse(Long id, String employeeNo, IntegrationAction action, IntegrationTaskStatus status,
        int retryCount, int maxRetryCount, String lastErrorCode, Instant createdAt, Instant updatedAt) {
    public static IntegrationTaskListResponse from(IntegrationTaskListRow row) {
        return new IntegrationTaskListResponse(row.id(), row.employeeNo(), row.action(), row.status(), row.retryCount(),
                row.maxRetryCount(), row.lastErrorCode(), row.createdAt(), row.updatedAt());
    }
}
