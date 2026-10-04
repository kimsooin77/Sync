package com.kimsooin77.sync.sync;

import com.kimsooin77.sync.employee.EmploymentStatus;

public record NormalizedEmployee(
        String employeeNo,
        String name,
        String email,
        String departmentCode,
        EmploymentStatus employmentStatus
) {
}
