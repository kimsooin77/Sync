package com.kimsooin77.sync.api.employee;

import com.kimsooin77.sync.employee.Employee;
import com.kimsooin77.sync.employee.EmployeeListRow;
import com.kimsooin77.sync.employee.EmploymentStatus;

import java.time.Instant;

public record EmployeeResponse(Long id, String employeeNo, String name, String email, String departmentCode,
                               EmploymentStatus employmentStatus, Instant createdAt, Instant updatedAt) {
    public static EmployeeResponse from(EmployeeListRow row) {
        return new EmployeeResponse(row.id(), row.employeeNo(), row.name(), row.email(), row.departmentCode(),
                row.employmentStatus(), row.createdAt(), row.updatedAt());
    }
    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(employee.getId(), employee.getEmployeeNo(), employee.getName(),
                employee.getCompanyEmail(), employee.getDepartmentCode(), employee.getEmploymentStatus(),
                employee.getCreatedAt(), employee.getUpdatedAt());
    }
}
