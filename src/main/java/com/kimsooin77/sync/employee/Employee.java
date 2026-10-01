package com.kimsooin77.sync.employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "employee", uniqueConstraints = {
        @UniqueConstraint(name = "uk_employee_employee_no", columnNames = "employee_no")
})
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_no", nullable = false, updatable = false, length = 64)
    private String employeeNo;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "company_email", length = 320)
    private String companyEmail;

    @Column(name = "department_code", length = 100)
    private String departmentCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_status", nullable = false, length = 20)
    private EmploymentStatus employmentStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Employee() {
    }

    public Employee(
            String employeeNo,
            String name,
            String companyEmail,
            String departmentCode,
            EmploymentStatus employmentStatus
    ) {
        this.employeeNo = requireText(employeeNo, "employeeNo");
        updateSnapshot(name, companyEmail, departmentCode, employmentStatus);
    }

    public void updateSnapshot(
            String name,
            String companyEmail,
            String departmentCode,
            EmploymentStatus employmentStatus
    ) {
        this.name = requireText(name, "name");
        this.companyEmail = companyEmail;
        this.departmentCode = departmentCode;
        this.employmentStatus = Objects.requireNonNull(employmentStatus, "employmentStatus");
    }

    @PrePersist
    void setCreationTimestamps() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void setUpdatedTimestamp() {
        updatedAt = Instant.now();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    public Long getId() {
        return id;
    }

    public String getEmployeeNo() {
        return employeeNo;
    }

    public String getName() {
        return name;
    }

    public String getCompanyEmail() {
        return companyEmail;
    }

    public String getDepartmentCode() {
        return departmentCode;
    }

    public EmploymentStatus getEmploymentStatus() {
        return employmentStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
