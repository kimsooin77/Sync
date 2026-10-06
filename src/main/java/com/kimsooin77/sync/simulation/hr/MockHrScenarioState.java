package com.kimsooin77.sync.simulation.hr;

import java.util.Locale;
import java.util.Objects;

public final class MockHrScenarioState {
    private volatile MockHrProperties.Scenario scenario;

    public MockHrScenarioState(MockHrProperties.Scenario initialScenario) {
        this.scenario = Objects.requireNonNull(initialScenario);
    }

    public MockHrProperties.Scenario current() {
        return scenario;
    }

    public synchronized MockHrProperties.Scenario changeTo(String value) {
        if (value == null) {
            throw new IllegalArgumentException("scenario is required");
        }
        try {
            scenario = MockHrProperties.Scenario.valueOf(value.strip().toUpperCase(Locale.ROOT).replace('-', '_'));
            return scenario;
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("scenario must be initial, changed, or partial-invalid", invalid);
        }
    }
}
