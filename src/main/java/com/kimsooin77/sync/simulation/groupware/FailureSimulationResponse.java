package com.kimsooin77.sync.simulation.groupware;

import java.util.Map;

public record FailureSimulationResponse(FailureRule defaultRule, Map<String, FailureRule> overrides) {
}
