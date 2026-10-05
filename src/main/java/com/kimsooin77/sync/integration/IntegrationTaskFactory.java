package com.kimsooin77.sync.integration;

import com.kimsooin77.sync.employee.Employee;
import com.kimsooin77.sync.employee.EmployeeSnapshot;
import com.kimsooin77.sync.employee.EmploymentStatus;
import com.kimsooin77.sync.sync.EmployeeChangeSet;
import com.kimsooin77.sync.sync.SyncItem;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.Optional;

@Component
public class IntegrationTaskFactory {

    private final ObjectMapper objectMapper;
    private final IdempotencyKeyGenerator idempotencyKeyGenerator;

    public IntegrationTaskFactory(
            ObjectMapper objectMapper,
            IdempotencyKeyGenerator idempotencyKeyGenerator
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.idempotencyKeyGenerator = Objects.requireNonNull(
                idempotencyKeyGenerator, "idempotencyKeyGenerator");
    }

    public Optional<IntegrationTask> forInserted(Employee employee, SyncItem syncItem) {
        Objects.requireNonNull(employee, "employee");
        Objects.requireNonNull(syncItem, "syncItem");
        IntegrationAction action = employee.getEmploymentStatus() == EmploymentStatus.TERMINATED
                ? IntegrationAction.DISABLE_ACCOUNT
                : IntegrationAction.CREATE_ACCOUNT;
        return Optional.of(create(employee, syncItem, action));
    }

    public IntegrationTask forUpdated(
            Employee employee,
            SyncItem syncItem,
            EmployeeChangeSet changeSet
    ) {
        Objects.requireNonNull(employee, "employee");
        Objects.requireNonNull(syncItem, "syncItem");
        Objects.requireNonNull(changeSet, "changeSet");
        if (!changeSet.hasChanges()) {
            throw new IllegalArgumentException("updated integration task requires an employee change");
        }

        EmploymentStatus previousStatus = changeSet.changes().stream()
                .filter(change -> change.fieldName().equals("employmentStatus"))
                .map(EmployeeChangeSet.FieldChange::before)
                .map(EmploymentStatus.class::cast)
                .findFirst()
                .orElse(employee.getEmploymentStatus());
        IntegrationAction action = employee.getEmploymentStatus() == EmploymentStatus.TERMINATED
                ? IntegrationAction.DISABLE_ACCOUNT
                : IntegrationAction.UPDATE_ACCOUNT;
        return create(employee, syncItem, action);
    }

    private IntegrationTask create(Employee employee, SyncItem syncItem, IntegrationAction action) {
        return new IntegrationTask(
                employee,
                syncItem,
                IntegrationTarget.GROUPWARE,
                action,
                serialize(EmployeeSnapshot.from(employee)),
                idempotencyKeyGenerator.generate());
    }

    private String serialize(EmployeeSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JacksonException serializationFailure) {
            throw new IllegalStateException("could not serialize integration task payload", serializationFailure);
        }
    }
}
