package com.kimsooin77.sync.integration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kimsooin77.sync.employee.EmployeeSnapshot;

public record GroupwareAccountRequest(
        @JsonProperty("employee_no") String employeeNo,
        String name,
        String email,
        @JsonProperty("department_code") String departmentCode,
        @JsonProperty("employment_status") String employmentStatus
) {

    static GroupwareAccountRequest from(EmployeeSnapshot snapshot) {
        return new GroupwareAccountRequest(
                snapshot.employeeNo(), snapshot.name(), snapshot.email(), snapshot.departmentCode(),
                snapshot.employmentStatus().name());
    }
}
