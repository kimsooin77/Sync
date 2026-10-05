package com.kimsooin77.sync.simulation.groupware;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FailureSimulation {
    private final MockGroupwareProperties properties;
    private final ConcurrentHashMap<String, FailureRule> overrides = new ConcurrentHashMap<>();

    public FailureSimulation(MockGroupwareProperties properties) {
        this.properties = properties;
    }

    public FailureRule forEmployee(String employeeNo) {
        return overrides.getOrDefault(normalize(employeeNo), properties.defaultRule());
    }

    public Map<String, FailureRule> overrides() {
        return Map.copyOf(overrides);
    }

    public void setOverride(String employeeNo, FailureRule rule) {
        overrides.put(normalize(employeeNo), rule);
    }

    public void removeOverride(String employeeNo) {
        overrides.remove(normalize(employeeNo));
    }

    public void clearOverrides() {
        overrides.clear();
    }

    private static String normalize(String employeeNo) {
        if (employeeNo == null || employeeNo.isBlank()) {
            throw new IllegalArgumentException("employeeNo is required");
        }
        return employeeNo.strip().toUpperCase(java.util.Locale.ROOT);
    }
}
