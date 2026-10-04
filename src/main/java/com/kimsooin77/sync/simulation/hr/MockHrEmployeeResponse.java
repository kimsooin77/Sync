package com.kimsooin77.sync.simulation.hr;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MockHrEmployeeResponse(
        @JsonProperty("employee_no") String employeeNo,
        @JsonProperty("employee_name") String employeeName,
        @JsonProperty("email") String email,
        @JsonProperty("department_code") String departmentCode,
        @JsonProperty("employment_status") String employmentStatus
) {
}
