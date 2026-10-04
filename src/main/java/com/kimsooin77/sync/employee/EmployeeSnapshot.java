package com.kimsooin77.sync.employee;

import java.util.Objects;

public record EmployeeSnapshot(
        String employeeNo,
        String name,
        String email,
        String departmentCode,
        EmploymentStatus employmentStatus
) {

    public EmployeeSnapshot {
        Objects.requireNonNull(employeeNo, "employeeNo");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(employmentStatus, "employmentStatus");
    }

    public static EmployeeSnapshot from(Employee employee) {
        Objects.requireNonNull(employee, "employee");
        return new EmployeeSnapshot(
                employee.getEmployeeNo(),
                employee.getName(),
                employee.getCompanyEmail(),
                employee.getDepartmentCode(),
                employee.getEmploymentStatus());
    }
}
