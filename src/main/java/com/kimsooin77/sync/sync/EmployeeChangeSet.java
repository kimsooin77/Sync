package com.kimsooin77.sync.sync;

import java.util.List;
import java.util.Objects;

public record EmployeeChangeSet(List<FieldChange> changes) {

    public EmployeeChangeSet {
        changes = List.copyOf(changes);
    }

    public boolean hasChanges() {
        return !changes.isEmpty();
    }

    public record FieldChange(String fieldName, Object before, Object after) {

        public FieldChange {
            Objects.requireNonNull(fieldName, "fieldName");
        }
    }
}
