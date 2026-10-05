package com.kimsooin77.sync.integration;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class IntegrationTaskTransactionService {

    private static final java.util.Set<String> MANUALLY_RETRYABLE_ERRORS = java.util.Set.of(
            "GROUPWARE_HTTP_ERROR", "GROUPWARE_CONNECTION_ERROR", "GROUPWARE_TIMEOUT",
            "GROUPWARE_RESPONSE_INVALID", "PROCESSING_RECOVERY_EXHAUSTED");

    private final IntegrationTaskRepository integrationTaskRepository;
    private final IntegrationAttemptRepository integrationAttemptRepository;

    public IntegrationTaskTransactionService(
            IntegrationTaskRepository integrationTaskRepository,
            IntegrationAttemptRepository integrationAttemptRepository
    ) {
        this.integrationTaskRepository = Objects.requireNonNull(integrationTaskRepository,
                "integrationTaskRepository");
        this.integrationAttemptRepository = Objects.requireNonNull(integrationAttemptRepository,
                "integrationAttemptRepository");
    }

    @Transactional(readOnly = true)
    public List<Long> findEligibleIds(int batchSize, Instant now) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        Objects.requireNonNull(now, "now");
        return integrationTaskRepository.findEligibleTasks(IntegrationTaskStatus.PENDING,
                        IntegrationTaskStatus.RETRY_WAIT, now, PageRequest.of(0, batchSize))
                .stream()
                .map(IntegrationTask::getId)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Long> findStaleProcessingIds(int batchSize, Instant cutoff) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        Objects.requireNonNull(cutoff, "cutoff");
        return integrationTaskRepository.findStaleProcessingIds(IntegrationTaskStatus.PROCESSING, cutoff,
                        PageRequest.of(0, batchSize));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recoverStaleProcessing(Long taskId, Instant cutoff, Instant now) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(cutoff, "cutoff");
        Objects.requireNonNull(now, "now");
        integrationTaskRepository.findStaleProcessingByIdForUpdate(taskId, IntegrationTaskStatus.PROCESSING,
                        cutoff)
                .ifPresent(task -> {
                    if (task.getRetryCount() >= task.getMaxRetryCount()) {
                        task.failRecoveryExhausted();
                    } else {
                        task.recoverToRetryWait(now);
                    }
                    integrationTaskRepository.flush();
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<IntegrationTaskCommand> markProcessing(Long taskId, Instant now) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(now, "now");
        return integrationTaskRepository.findEligibleByIdForUpdate(taskId, IntegrationTaskStatus.PENDING,
                        IntegrationTaskStatus.RETRY_WAIT, now)
                .map(task -> {
                    task.markProcessing(now);
                    integrationTaskRepository.flush();
                    return new IntegrationTaskCommand(task.getId(), task.getTarget(), task.getAction(),
                            task.getPayload(), task.getIdempotencyKey(), task.getRetryCount(),
                            task.getMaxRetryCount());
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPayloadFailed(Long taskId, String errorCode, String safeErrorMessage) {
        IntegrationTask task = processingTask(taskId);
        task.markFailed(errorCode, safeErrorMessage);
        integrationTaskRepository.flush();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSucceeded(Long taskId, Instant startedAt, Instant finishedAt, int httpStatus) {
        IntegrationTask task = processingTask(taskId);
        integrationAttemptRepository.saveAndFlush(IntegrationAttempt.succeeded(task, nextAttemptNo(task),
                startedAt, finishedAt, httpStatus));
        task.markSucceeded();
        integrationTaskRepository.flush();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailed(
            Long taskId,
            Instant startedAt,
            Instant finishedAt,
            Integer httpStatus,
            String errorCode,
            String safeErrorMessage,
            Instant retryAt
    ) {
        IntegrationTask task = processingTask(taskId);
        integrationAttemptRepository.saveAndFlush(IntegrationAttempt.failed(
                task, nextAttemptNo(task), startedAt, finishedAt, httpStatus, errorCode, safeErrorMessage));
        if (retryAt != null) {
            task.markRetryWait(errorCode, safeErrorMessage, retryAt);
        } else {
            task.markFailed(errorCode, safeErrorMessage);
        }
        integrationTaskRepository.flush();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IntegrationTaskRetryResult retryFailedTask(Long taskId) {
        Objects.requireNonNull(taskId, "taskId");
        IntegrationTask task = integrationTaskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new IntegrationTaskNotFoundException(taskId));
        if (task.getStatus() != IntegrationTaskStatus.FAILED) {
            throw new IntegrationTaskRetryRejectedException("TASK_NOT_FAILED");
        }
        if (!MANUALLY_RETRYABLE_ERRORS.contains(task.getLastErrorCode())) {
            throw new IntegrationTaskRetryRejectedException("TASK_RETRY_NOT_ALLOWED");
        }
        task.resetForManualRetry();
        integrationTaskRepository.flush();
        return new IntegrationTaskRetryResult(task.getId(), task.getStatus(), task.getRetryCount());
    }

    private IntegrationTask processingTask(Long taskId) {
        Objects.requireNonNull(taskId, "taskId");
        return integrationTaskRepository.findProcessingByIdForUpdate(taskId, IntegrationTaskStatus.PROCESSING)
                .orElseThrow(() -> new IllegalStateException("integration task is not processing"));
    }

    private int nextAttemptNo(IntegrationTask task) {
        Integer maximumAttemptNo = integrationAttemptRepository.findMaximumAttemptNo(task.getId());
        return maximumAttemptNo == null ? 1 : maximumAttemptNo + 1;
    }
}
