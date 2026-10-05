package com.kimsooin77.sync.integration;

public record IntegrationTaskRetryResult(Long id, IntegrationTaskStatus status, int retryCount) {
}
