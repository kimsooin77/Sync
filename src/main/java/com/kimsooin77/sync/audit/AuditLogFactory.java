package com.kimsooin77.sync.audit;

import com.kimsooin77.sync.employee.Employee;
import com.kimsooin77.sync.employee.EmployeeSnapshot;
import com.kimsooin77.sync.sync.EmployeeChangeSet;
import com.kimsooin77.sync.sync.SyncItem;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Component
public class AuditLogFactory {

    private final ObjectMapper objectMapper;

    public AuditLogFactory(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public AuditLog created(Employee employee, SyncItem syncItem) {
        return new AuditLog(
                employee,
                syncItem,
                AuditAction.CREATED,
                serialize(EmployeeSnapshot.from(employee)),
                AuditSource.HR_SYNC);
    }

    public AuditLog updated(Employee employee, SyncItem syncItem, EmployeeChangeSet changeSet) {
        Objects.requireNonNull(changeSet, "changeSet");
        if (!changeSet.hasChanges()) {
            throw new IllegalArgumentException("updated audit log requires at least one field change");
        }

        Map<String, ChangeValues> changes = new LinkedHashMap<>();
        for (EmployeeChangeSet.FieldChange change : changeSet.changes()) {
            changes.put(change.fieldName(), new ChangeValues(change.before(), change.after()));
        }
        return new AuditLog(
                employee,
                syncItem,
                AuditAction.UPDATED,
                serialize(changes),
                AuditSource.HR_SYNC);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException serializationFailure) {
            throw new IllegalStateException("could not serialize audit changes", serializationFailure);
        }
    }

    private record ChangeValues(Object before, Object after) {
    }
}
