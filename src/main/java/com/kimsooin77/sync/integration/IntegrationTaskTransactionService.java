package com.kimsooin77.sync.integration;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class IntegrationTaskTransactionService {

    private final IntegrationTaskRepository integrationTaskRepository;

    public IntegrationTaskTransactionService(IntegrationTaskRepository integrationTaskRepository) {
        this.integrationTaskRepository = Objects.requireNonNull(integrationTaskRepository,
                "integrationTaskRepository");
    }

    @Transactional(readOnly = true)
    public List<Long> findPendingIds(int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        return integrationTaskRepository.findByStatusOrderByCreatedAtAscIdAsc(
                        IntegrationTaskStatus.PENDING, PageRequest.of(0, batchSize))
                .stream()
                .map(IntegrationTask::getId)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<IntegrationTaskCommand> markProcessing(Long taskId) {
        Objects.requireNonNull(taskId, "taskId");
        return integrationTaskRepository.findByIdAndStatusForUpdate(taskId, IntegrationTaskStatus.PENDING)
                .map(task -> {
                    task.markProcessing();
                    integrationTaskRepository.flush();
                    return new IntegrationTaskCommand(task.getId(), task.getTarget(), task.getAction(),
                            task.getPayload(), task.getIdempotencyKey());
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSucceeded(Long taskId) {
        IntegrationTask task = processingTask(taskId);
        task.markSucceeded();
        integrationTaskRepository.flush();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long taskId, String errorCode, String safeErrorMessage) {
        IntegrationTask task = processingTask(taskId);
        task.markFailed(errorCode, safeErrorMessage);
        integrationTaskRepository.flush();
    }

    private IntegrationTask processingTask(Long taskId) {
        Objects.requireNonNull(taskId, "taskId");
        return integrationTaskRepository.findByIdAndStatusForUpdate(taskId, IntegrationTaskStatus.PROCESSING)
                .orElseThrow(() -> new IllegalStateException("integration task is not processing"));
    }
}
