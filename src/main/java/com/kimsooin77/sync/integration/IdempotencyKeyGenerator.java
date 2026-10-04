package com.kimsooin77.sync.integration;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class IdempotencyKeyGenerator {

    public UUID generate() {
        return UUID.randomUUID();
    }
}
