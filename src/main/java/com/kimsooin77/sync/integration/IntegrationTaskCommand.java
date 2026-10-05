package com.kimsooin77.sync.integration;

import java.util.UUID;

record IntegrationTaskCommand(
        Long id,
        IntegrationTarget target,
        IntegrationAction action,
        String payload,
        UUID idempotencyKey
) {
}
