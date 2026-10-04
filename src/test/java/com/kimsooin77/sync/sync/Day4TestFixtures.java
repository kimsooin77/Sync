package com.kimsooin77.sync.sync;

import com.kimsooin77.sync.employee.Employee;

public final class Day4TestFixtures {

    private Day4TestFixtures() {
    }

    public static SyncItem insertedItem(Employee employee) {
        return SyncItem.inserted(new SyncJob(1), 1, employee);
    }

    public static SyncItem updatedItem(Employee employee) {
        return SyncItem.updated(new SyncJob(1), 1, employee);
    }
}
