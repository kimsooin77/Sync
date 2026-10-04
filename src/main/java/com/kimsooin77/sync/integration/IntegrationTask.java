package com.kimsooin77.sync.integration;

import com.kimsooin77.sync.employee.Employee;
import com.kimsooin77.sync.sync.SyncItem;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "integration_task", uniqueConstraints = {
        @UniqueConstraint(name = "uk_integration_task_sync_item_target", columnNames = {"sync_item_id", "target"})
})
public class IntegrationTask {

    private static final int DEFAULT_MAX_RETRY_COUNT = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sync_item_id", nullable = false)
    private SyncItem syncItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IntegrationTarget target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IntegrationAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IntegrationTaskStatus status;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String payload;

    @Column(name = "idempotency_key", nullable = false, unique = true, updatable = false)
    private UUID idempotencyKey;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "max_retry_count", nullable = false)
    private int maxRetryCount;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "last_error_code", columnDefinition = "text")
    private String lastErrorCode;

    @Column(name = "last_error_message", columnDefinition = "text")
    private String lastErrorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IntegrationTask() {
    }

    IntegrationTask(
            Employee employee,
            SyncItem syncItem,
            IntegrationTarget target,
            IntegrationAction action,
            String payload,
            UUID idempotencyKey
    ) {
        this.employee = Objects.requireNonNull(employee, "employee");
        this.syncItem = Objects.requireNonNull(syncItem, "syncItem");
        this.target = Objects.requireNonNull(target, "target");
        this.action = Objects.requireNonNull(action, "action");
        this.payload = Objects.requireNonNull(payload, "payload");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.status = IntegrationTaskStatus.PENDING;
        this.retryCount = 0;
        this.maxRetryCount = DEFAULT_MAX_RETRY_COUNT;
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

    public Long getId() {
        return id;
    }

    public Long getEmployeeId() {
        return employee.getId();
    }

    public Long getSyncItemId() {
        return syncItem.getId();
    }

    public IntegrationTarget getTarget() {
        return target;
    }

    public IntegrationAction getAction() {
        return action;
    }

    public IntegrationTaskStatus getStatus() {
        return status;
    }

    public String getPayload() {
        return payload;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public int getMaxRetryCount() {
        return maxRetryCount;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
