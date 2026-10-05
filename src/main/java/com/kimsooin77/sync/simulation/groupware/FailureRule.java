package com.kimsooin77.sync.simulation.groupware;

public record FailureRule(FailureMode mode, long delayMs) {
    public FailureRule {
        if (mode == null) {
            throw new IllegalArgumentException("mode is required");
        }
        if (delayMs < 0) {
            throw new IllegalArgumentException("delayMs must not be negative");
        }
        if (mode != FailureMode.DELAY && delayMs != 0) {
            throw new IllegalArgumentException("delayMs is only valid for DELAY mode");
        }
    }
}
