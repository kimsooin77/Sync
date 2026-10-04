package com.kimsooin77.sync.audit;

import com.kimsooin77.sync.employee.Employee;
import com.kimsooin77.sync.employee.EmploymentStatus;
import com.kimsooin77.sync.sync.Day4TestFixtures;
import com.kimsooin77.sync.sync.EmployeeChangeSet;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogFactoryTest {

    private final AuditLogFactory auditLogFactory = new AuditLogFactory(JsonMapper.builder().build());

    @Test
    void createdAuditStoresFullEmployeeSnapshotIncludingNullOptionalFields() {
        Employee employee = new Employee("E-1001", "Alice", null, null, EmploymentStatus.ACTIVE);

        AuditLog auditLog = auditLogFactory.created(employee, Day4TestFixtures.insertedItem(employee));

        assertThat(auditLog.getAction()).isEqualTo(AuditAction.CREATED);
        assertThat(auditLog.getSource()).isEqualTo(AuditSource.HR_SYNC);
        assertThat(auditLog.getChanges())
                .contains("\"employeeNo\":\"E-1001\"")
                .contains("\"name\":\"Alice\"")
                .contains("\"email\":null")
                .contains("\"departmentCode\":null")
                .contains("\"employmentStatus\":\"ACTIVE\"");
    }

    @Test
    void updatedAuditStoresOnlyTheSuppliedChangedFieldsAndKeepsNullValues() {
        Employee employee = new Employee("E-1002", "Alice", null, "DEV01", EmploymentStatus.ACTIVE);
        EmployeeChangeSet changeSet = new EmployeeChangeSet(List.of(
                new EmployeeChangeSet.FieldChange("email", "alice@old.example", null),
                new EmployeeChangeSet.FieldChange("departmentCode", "DEV01", "DEV02")));

        AuditLog auditLog = auditLogFactory.updated(
                employee, Day4TestFixtures.updatedItem(employee), changeSet);

        assertThat(auditLog.getAction()).isEqualTo(AuditAction.UPDATED);
        assertThat(auditLog.getChanges())
                .contains("\"email\":{\"before\":\"alice@old.example\",\"after\":null}")
                .contains("\"departmentCode\":{\"before\":\"DEV01\",\"after\":\"DEV02\"}")
                .doesNotContain("\"name\"");
    }
}
