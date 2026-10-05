package com.kimsooin77.sync.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(prefix = "app.integration.worker")
public record IntegrationWorkerProperties(boolean enabled, Duration fixedDelay, int batchSize) {

    public IntegrationWorkerProperties {
        Objects.requireNonNull(fixedDelay, "fixedDelay");
        if (fixedDelay.isNegative() || fixedDelay.isZero()) {
            throw new IllegalArgumentException("fixedDelay must be positive");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
    }
}
