package com.kimsooin77.sync.employee;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class EmployeeQueryService {
    private final EmployeeRepository repository;
    public EmployeeQueryService(EmployeeRepository repository) { this.repository = repository; }
    @Transactional(readOnly = true)
    public Page<EmployeeListRow> search(String keyword, EmploymentStatus status, Pageable pageable) {
        String normalized = keyword == null || keyword.isBlank() ? "" : keyword.strip();
        return repository.search(normalized, status, pageable);
    }
    @Transactional(readOnly = true)
    public Optional<Employee> find(Long id) { return repository.findById(id); }
    @Transactional(readOnly = true)
    public boolean exists(Long id) { return repository.existsById(id); }
}
