package com.kimsooin77.sync.sync;

import com.kimsooin77.sync.employee.Employee;
import com.kimsooin77.sync.employee.EmploymentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmployeeComparatorTest {

    private final EmployeeComparator comparator = new EmployeeComparator();

    @Test
    void reportsNoChangesWhenAllComparedFieldsMatch() {
        Employee employee = employee("Alice", "alice@example.com", "ENG", EmploymentStatus.ACTIVE);

        EmployeeChangeSet changes = comparator.compare(employee, normalized(
                "Alice", "alice@example.com", "ENG", EmploymentStatus.ACTIVE));

        assertThat(changes.hasChanges()).isFalse();
        assertThat(changes.changes()).isEmpty();
    }

    @Test
    void reportsChangedFieldsInStableOrderIncludingNullTransitions() {
        Employee employee = employee("Old name", "alice@example.com", "ENG", EmploymentStatus.ACTIVE);

        EmployeeChangeSet changes = comparator.compare(employee, normalized(
                "New name", null, null, EmploymentStatus.ON_LEAVE));

        assertThat(changes.hasChanges()).isTrue();
        assertThat(changes.changes()).containsExactly(
                new EmployeeChangeSet.FieldChange("name", "Old name", "New name"),
                new EmployeeChangeSet.FieldChange("email", "alice@example.com", null),
                new EmployeeChangeSet.FieldChange("departmentCode", "ENG", null),
                new EmployeeChangeSet.FieldChange("employmentStatus", EmploymentStatus.ACTIVE,
                        EmploymentStatus.ON_LEAVE));
    }

    private static Employee employee(
            String name,
            String email,
            String departmentCode,
            EmploymentStatus status
    ) {
        return new Employee("E1001", name, email, departmentCode, status);
    }

    private static NormalizedEmployee normalized(
            String name,
            String email,
            String departmentCode,
            EmploymentStatus status
    ) {
        return new NormalizedEmployee("E1001", name, email, departmentCode, status);
    }
}
