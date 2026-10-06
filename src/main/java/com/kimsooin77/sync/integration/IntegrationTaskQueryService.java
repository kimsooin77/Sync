package com.kimsooin77.sync.integration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
@Service
public class IntegrationTaskQueryService {
    private final IntegrationTaskRepository tasks;
    private final IntegrationAttemptRepository attempts;
    public IntegrationTaskQueryService(IntegrationTaskRepository tasks, IntegrationAttemptRepository attempts) { this.tasks = tasks; this.attempts = attempts; }
    @Transactional(readOnly = true)
    public Page<IntegrationTaskListRow> search(IntegrationTaskStatus status, IntegrationAction action, String employeeNo, Pageable pageable) {
        return tasks.search(status, action, employeeNo == null || employeeNo.isBlank() ? null : employeeNo.strip(), pageable);
    }
    @Transactional(readOnly = true)
    public Optional<IntegrationTaskDetailRow> detail(Long id) { return tasks.findDetailById(id); }
    @Transactional(readOnly = true)
    public Page<IntegrationAttempt> attempts(Long id, Pageable pageable) {
        if (!tasks.existsById(id)) throw new IntegrationTaskNotFoundException(id);
        return attempts.findAllByIntegrationTask_IdOrderByAttemptNoAsc(id, pageable);
    }
}
