package com.kimsooin77.sync.api.integration;

import com.kimsooin77.sync.integration.IntegrationTaskRetryResult;
import com.kimsooin77.sync.integration.IntegrationTaskStatus;

public record IntegrationTaskRetryResponse(Long id, IntegrationTaskStatus status, int retryCount) {
    static IntegrationTaskRetryResponse from(IntegrationTaskRetryResult result) {
        return new IntegrationTaskRetryResponse(result.id(), result.status(), result.retryCount());
    }
}
