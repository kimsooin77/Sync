package com.kimsooin77.sync.audit;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "audit_log", uniqueConstraints = {
        @UniqueConstraint(name = "uk_audit_log_sync_item", columnNames = "sync_item_id")
})
public class AuditLog {

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
    private AuditAction action;

    @Column(nullable = false, columnDefinition = "text")
    private String changes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditSource source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    AuditLog(Employee employee, SyncItem syncItem, AuditAction action, String changes, AuditSource source) {
        this.employee = Objects.requireNonNull(employee, "employee");
        this.syncItem = Objects.requireNonNull(syncItem, "syncItem");
        this.action = Objects.requireNonNull(action, "action");
        this.changes = Objects.requireNonNull(changes, "changes");
        this.source = Objects.requireNonNull(source, "source");
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

    public Long getEmployeeId() {
        return employee.getId();
    }

    public Long getSyncItemId() {
        return syncItem.getId();
    }

    public AuditAction getAction() {
        return action;
    }

    public String getChanges() {
        return changes;
    }

    public AuditSource getSource() {
        return source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
