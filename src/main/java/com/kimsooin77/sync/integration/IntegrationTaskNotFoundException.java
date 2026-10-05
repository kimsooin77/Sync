package com.kimsooin77.sync.integration;

public class IntegrationTaskNotFoundException extends RuntimeException {
    public IntegrationTaskNotFoundException(Long id) {
        super("Integration task was not found: " + id);
    }
}
