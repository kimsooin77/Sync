package com.kimsooin77.sync.integration;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

@Component
public class IntegrationRetryPolicy {

    private static final Duration[] BACKOFF = {
            Duration.ofSeconds(5), Duration.ofSeconds(15), Duration.ofSeconds(30)
    };
    private static final Set<Integer> RETRYABLE_HTTP_STATUSES = Set.of(429, 500, 502, 503, 504);

    public Optional<Instant> nextRetryAt(int retryCount, int maxRetryCount, Instant finishedAt) {
        if (retryCount < 0 || maxRetryCount < 0) {
            throw new IllegalArgumentException("retry counts must not be negative");
        }
        if (retryCount >= maxRetryCount || retryCount >= BACKOFF.length) {
            return Optional.empty();
        }
        return Optional.of(finishedAt.plus(BACKOFF[retryCount]));
    }

    public boolean isRetryable(GroupwareClientException failure) {
        if (failure.getHttpStatus() != null) {
            return RETRYABLE_HTTP_STATUSES.contains(failure.getHttpStatus());
        }
        return "GROUPWARE_CONNECTION_ERROR".equals(failure.getErrorCode())
                || "GROUPWARE_TIMEOUT".equals(failure.getErrorCode());
    }
}
