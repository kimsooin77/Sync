package com.kimsooin77.sync.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(prefix = "app.integration.worker")
public record IntegrationWorkerProperties(boolean enabled, Duration fixedDelay, int batchSize,
                                          Duration recoveryThreshold) {

    public IntegrationWorkerProperties {
        Objects.requireNonNull(fixedDelay, "fixedDelay");
        Objects.requireNonNull(recoveryThreshold, "recoveryThreshold");
        if (fixedDelay.isNegative() || fixedDelay.isZero()) {
            throw new IllegalArgumentException("fixedDelay must be positive");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (recoveryThreshold.isNegative() || recoveryThreshold.isZero()) {
            throw new IllegalArgumentException("recoveryThreshold must be positive");
        }
    }
}
