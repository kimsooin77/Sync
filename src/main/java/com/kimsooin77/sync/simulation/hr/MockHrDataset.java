package com.kimsooin77.sync.simulation.hr;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "app.mock.hr", name = "enabled", havingValue = "true")
public class MockHrDataset {

    private final MockHrScenarioState scenarioState;

    public MockHrDataset(MockHrScenarioState scenarioState) {
        this.scenarioState = scenarioState;
    }

    public List<MockHrEmployeeResponse> employees() {
        return switch (scenarioState.current()) {
            case INITIAL -> List.of(
                    employee("E1001", "김수인", "sooin@company.com", "DEV01", "ACTIVE"),
                    employee("E1002", "홍길동", "hong@company.com", "DEV01", "ACTIVE"),
                    employee("E1003", "이민지", "minji@company.com", "OPS01", "ON_LEAVE"));
            case CHANGED -> List.of(
                    employee("E1001", "김수인", "sooin@company.com", "DEV01", "ACTIVE"),
                    employee("E1002", "홍길동", "hong@company.com", "DEV02", "ACTIVE"),
                    employee("E1003", "이민지", "minji@company.com", "OPS01", "TERMINATED"));
            case PARTIAL_INVALID -> List.of(
                    employee("E1001", "김수인", "sooin@company.com", "DEV01", "ACTIVE"),
                    employee("E1002", "홍길동", "hong@company.com", "DEV01", "UNKNOWN"),
                    employee("E1003", "이민지", "minji@company.com", "OPS01", "ON_LEAVE"));
        };
    }

    private static MockHrEmployeeResponse employee(
            String employeeNo,
            String employeeName,
            String email,
            String departmentCode,
            String employmentStatus
    ) {
        return new MockHrEmployeeResponse(employeeNo, employeeName, email, departmentCode, employmentStatus);
    }
}
