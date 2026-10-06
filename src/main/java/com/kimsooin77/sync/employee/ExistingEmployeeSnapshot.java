package com.kimsooin77.sync.employee;

public record ExistingEmployeeSnapshot(
        Long employeeId,
        String employeeNo,
        String name,
        String email,
        String departmentCode,
        EmploymentStatus employmentStatus
) {
}
