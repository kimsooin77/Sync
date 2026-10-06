package com.kimsooin77.sync.employee;

import java.time.Instant;

public record EmployeeListRow(Long id, String employeeNo, String name, String email, String departmentCode,
                              EmploymentStatus employmentStatus, Instant createdAt, Instant updatedAt) { }
