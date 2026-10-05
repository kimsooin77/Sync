package com.kimsooin77.sync.integration;

public class IntegrationTaskRetryRejectedException extends RuntimeException {
    private final String code;

    public IntegrationTaskRetryRejectedException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
