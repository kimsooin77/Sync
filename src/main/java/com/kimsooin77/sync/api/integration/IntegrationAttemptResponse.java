package com.kimsooin77.sync.api.integration;
import com.kimsooin77.sync.integration.IntegrationAttempt;
import com.kimsooin77.sync.integration.IntegrationAttemptResult;
import java.time.Instant;
public record IntegrationAttemptResponse(Long id, int attemptNo, Instant startedAt, Instant finishedAt,
        IntegrationAttemptResult result, Integer httpStatus, String errorCode, String errorMessage, Instant createdAt) {
    public static IntegrationAttemptResponse from(IntegrationAttempt a) {
        return new IntegrationAttemptResponse(a.getId(), a.getAttemptNo(), a.getStartedAt(), a.getFinishedAt(),
                a.getResult(), a.getHttpStatus(), a.getErrorCode(), a.getErrorMessage(), a.getCreatedAt());
    }
}
