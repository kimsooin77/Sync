package com.kimsooin77.sync.sync;

import com.kimsooin77.sync.employee.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "sync_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sync_item_job_row", columnNames = {"sync_job_id", "row_number"})
})
public class SyncItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sync_job_id", nullable = false)
    private SyncJob syncJob;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "employee_no", columnDefinition = "text")
    private String employeeNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SyncItemResult result;

    @Column(name = "error_code", columnDefinition = "text")
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SyncItem() {
    }

    private SyncItem(
            SyncJob syncJob,
            int rowNumber,
            String employeeNo,
            SyncItemResult result,
            String errorCode,
            String errorMessage,
            Employee employee
    ) {
        this.syncJob = Objects.requireNonNull(syncJob, "syncJob");
        if (rowNumber < 1) {
            throw new IllegalArgumentException("rowNumber must be positive");
        }
        this.rowNumber = rowNumber;
        this.employeeNo = employeeNo;
        this.result = Objects.requireNonNull(result, "result");
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.employee = employee;
    }

    static SyncItem inserted(SyncJob job, int rowNumber, Employee employee) {
        return new SyncItem(job, rowNumber, employee.getEmployeeNo(), SyncItemResult.INSERTED, null, null, employee);
    }

    static SyncItem updated(SyncJob job, int rowNumber, Employee employee) {
        return new SyncItem(job, rowNumber, employee.getEmployeeNo(), SyncItemResult.UPDATED, null, null, employee);
    }

    static SyncItem skipped(SyncJob job, int rowNumber, Employee employee) {
        return new SyncItem(
                job, rowNumber, employee.getEmployeeNo(), SyncItemResult.SKIPPED, null, null, employee);
    }

    static SyncItem failed(
            SyncJob job,
            int rowNumber,
            String employeeNo,
            String errorCode,
            String errorMessage
    ) {
        return new SyncItem(job, rowNumber, employeeNo, SyncItemResult.FAILED, errorCode, errorMessage, null);
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getSyncJobId() {
        return syncJob.getId();
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public String getEmployeeNo() {
        return employeeNo;
    }

    public SyncItemResult getResult() {
        return result;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Long getEmployeeId() {
        return employee == null ? null : employee.getId();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
