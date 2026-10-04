package com.kimsooin77.sync.sync;

import com.kimsooin77.sync.employee.Employee;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class EmployeeComparator {

    public EmployeeChangeSet compare(Employee current, NormalizedEmployee incoming) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(incoming, "incoming");

        List<EmployeeChangeSet.FieldChange> changes = new ArrayList<>();
        addIfChanged(changes, "name", current.getName(), incoming.name());
        addIfChanged(changes, "email", current.getCompanyEmail(), incoming.email());
        addIfChanged(changes, "departmentCode", current.getDepartmentCode(), incoming.departmentCode());
        addIfChanged(changes, "employmentStatus", current.getEmploymentStatus(), incoming.employmentStatus());
        return new EmployeeChangeSet(changes);
    }

    private static void addIfChanged(
            List<EmployeeChangeSet.FieldChange> changes,
            String fieldName,
            Object before,
            Object after
    ) {
        if (!Objects.equals(before, after)) {
            changes.add(new EmployeeChangeSet.FieldChange(fieldName, before, after));
        }
    }
}
