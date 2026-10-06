package com.kimsooin77.sync.sync;

import com.kimsooin77.sync.employee.EmployeeRepository;
import com.kimsooin77.sync.employee.ExistingEmployeeSnapshot;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ExistingEmployeeLookupService {

    static final int CHUNK_SIZE = 1_000;

    private final EmployeeRepository employeeRepository;

    public ExistingEmployeeLookupService(EmployeeRepository employeeRepository) {
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository");
    }

    public Map<String, ExistingEmployeeSnapshot> findExisting(List<String> employeeNumbers) {
        Objects.requireNonNull(employeeNumbers, "employeeNumbers");
        if (employeeNumbers.isEmpty()) return Map.of();

        Map<String, ExistingEmployeeSnapshot> snapshots = new HashMap<>();
        for (int start = 0; start < employeeNumbers.size(); start += CHUNK_SIZE) {
            int end = Math.min(start + CHUNK_SIZE, employeeNumbers.size());
            List<String> chunk = new ArrayList<>(employeeNumbers.subList(start, end));
            for (ExistingEmployeeSnapshot snapshot : employeeRepository.findSnapshotsByEmployeeNoIn(chunk)) {
                snapshots.put(snapshot.employeeNo(), snapshot);
            }
        }
        return Map.copyOf(snapshots);
    }
}
