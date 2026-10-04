package com.kimsooin77.sync.sync;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record HrEmployeeResponse(
        String employeeNo,
        String employeeName,
        String email,
        String departmentCode,
        String employmentStatus
) {
}
