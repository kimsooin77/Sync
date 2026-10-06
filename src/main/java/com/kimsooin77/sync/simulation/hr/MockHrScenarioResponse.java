package com.kimsooin77.sync.simulation.hr;

import java.util.Locale;

public record MockHrScenarioResponse(String scenario) {
    static MockHrScenarioResponse from(MockHrProperties.Scenario scenario) {
        return new MockHrScenarioResponse(scenario.name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }
}
