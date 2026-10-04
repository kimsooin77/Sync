package com.kimsooin77.sync.simulation.hr;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mock.hr")
public record MockHrProperties(boolean enabled, Scenario scenario) {

    public enum Scenario {
        INITIAL,
        CHANGED,
        PARTIAL_INVALID
    }
}
