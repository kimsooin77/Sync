package com.kimsooin77.sync.integration;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationRetryPolicyTest {

    private final IntegrationRetryPolicy policy = new IntegrationRetryPolicy();

    @Test
    void retryableHttpStatusesAreLimitedTo429AndSelectedServerErrors() {
        for (int status : new int[]{429, 500, 502, 503, 504}) {
            assertThat(policy.isRetryable(failure("GROUPWARE_HTTP_ERROR", status))).isTrue();
        }
        for (int status : new int[]{400, 401, 403, 404, 501}) {
            assertThat(policy.isRetryable(failure("GROUPWARE_HTTP_ERROR", status))).isFalse();
        }
    }

    @Test
    void connectionAndTimeoutFailuresAreRetryableButInvalidResponsesAreNot() {
        assertThat(policy.isRetryable(failure("GROUPWARE_CONNECTION_ERROR", null))).isTrue();
        assertThat(policy.isRetryable(failure("GROUPWARE_TIMEOUT", null))).isTrue();
        assertThat(policy.isRetryable(failure("GROUPWARE_RESPONSE_INVALID", null))).isFalse();
    }

    @Test
    void backoffIsFiveFifteenAndThirtySecondsAndStopsAfterThreeRetries() {
        Instant finishedAt = Instant.parse("2026-10-05T00:00:00Z");

        assertThat(policy.nextRetryAt(0, 3, finishedAt)).contains(finishedAt.plusSeconds(5));
        assertThat(policy.nextRetryAt(1, 3, finishedAt)).contains(finishedAt.plusSeconds(15));
        assertThat(policy.nextRetryAt(2, 3, finishedAt)).contains(finishedAt.plusSeconds(30));
        assertThat(policy.nextRetryAt(3, 3, finishedAt)).isEmpty();
    }

    private static GroupwareClientException failure(String code, Integer status) {
        return new GroupwareClientException(code, "safe", status, null);
    }
}
