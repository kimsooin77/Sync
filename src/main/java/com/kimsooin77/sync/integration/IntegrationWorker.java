package com.kimsooin77.sync.integration;

import com.kimsooin77.sync.employee.EmployeeSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class IntegrationWorker {

    private final IntegrationTaskTransactionService transactionService;
    private final GroupwareClient groupwareClient;
    private final ObjectMapper objectMapper;
    private final IntegrationWorkerProperties properties;
    private final IntegrationRetryPolicy retryPolicy;
    private final Clock clock;

    public IntegrationWorker(
            IntegrationTaskTransactionService transactionService,
            GroupwareClient groupwareClient,
            ObjectMapper objectMapper,
            IntegrationWorkerProperties properties,
            IntegrationRetryPolicy retryPolicy,
            Clock clock
    ) {
        this.transactionService = Objects.requireNonNull(transactionService, "transactionService");
        this.groupwareClient = Objects.requireNonNull(groupwareClient, "groupwareClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(propagation = Propagation.NEVER)
    public int runBatch() {
        Instant now = clock.instant();
        Instant recoveryCutoff = now.minus(properties.recoveryThreshold());
        List<Long> staleTaskIds = transactionService.findStaleProcessingIds(properties.batchSize(), recoveryCutoff);
        for (Long taskId : staleTaskIds) {
            transactionService.recoverStaleProcessing(taskId, recoveryCutoff, now);
        }

        List<Long> taskIds = transactionService.findEligibleIds(properties.batchSize(), clock.instant());
        int processed = 0;
        for (Long taskId : taskIds) {
            var command = transactionService.markProcessing(taskId, clock.instant());
            if (command.isPresent()) {
                process(command.get());
                processed++;
            }
        }
        return processed;
    }

    private void process(IntegrationTaskCommand task) {
        GroupwareAccountRequest request;
        try {
            if (task.target() != IntegrationTarget.GROUPWARE) {
                throw new InvalidTaskPayloadException();
            }
            EmployeeSnapshot snapshot = objectMapper.readValue(task.payload(), EmployeeSnapshot.class);
            if (snapshot == null || snapshot.employeeNo().isBlank() || snapshot.name().isBlank()) {
                throw new InvalidTaskPayloadException();
            }
            request = GroupwareAccountRequest.from(snapshot);
        } catch (JacksonException | InvalidTaskPayloadException | NullPointerException invalidPayload) {
            transactionService.markPayloadFailed(task.id(), "TASK_PAYLOAD_INVALID",
                    "Integration task payload is invalid");
            return;
        }

        Instant startedAt = clock.instant();
        GroupwareCallResult result = null;
        GroupwareClientException failure = null;
        try {
            result = groupwareClient.send(task.action(), request, task.idempotencyKey());
        } catch (GroupwareClientException externalFailure) {
            failure = externalFailure;
        }
        Instant finishedAt = clock.instant();

        if (failure == null) {
            transactionService.recordSucceeded(task.id(), startedAt, finishedAt,
                    result.httpStatus());
            return;
        }

        String safeMessage = safeMessage(failure.getErrorCode());
        Instant retryAt = retryPolicy.isRetryable(failure)
                ? retryPolicy.nextRetryAt(task.retryCount(), task.maxRetryCount(), finishedAt).orElse(null)
                : null;
        transactionService.recordFailed(task.id(), startedAt, finishedAt,
                failure.getHttpStatus(), failure.getErrorCode(), safeMessage, retryAt);
    }

    private static String safeMessage(String errorCode) {
        return switch (errorCode) {
            case "GROUPWARE_HTTP_ERROR" -> "Groupware returned an HTTP error";
            case "GROUPWARE_CONNECTION_ERROR" -> "Could not connect to Groupware";
            case "GROUPWARE_TIMEOUT" -> "Groupware request timed out";
            case "GROUPWARE_RESPONSE_INVALID" -> "Groupware response could not be processed";
            default -> "Integration task could not be processed";
        };
    }

    private static final class InvalidTaskPayloadException extends RuntimeException {
    }
}
