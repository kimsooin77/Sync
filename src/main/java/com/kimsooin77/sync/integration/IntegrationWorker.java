package com.kimsooin77.sync.integration;

import com.kimsooin77.sync.employee.EmployeeSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;

@Service
public class IntegrationWorker {

    private final IntegrationTaskTransactionService transactionService;
    private final GroupwareClient groupwareClient;
    private final ObjectMapper objectMapper;
    private final IntegrationWorkerProperties properties;

    public IntegrationWorker(
            IntegrationTaskTransactionService transactionService,
            GroupwareClient groupwareClient,
            ObjectMapper objectMapper,
            IntegrationWorkerProperties properties
    ) {
        this.transactionService = Objects.requireNonNull(transactionService, "transactionService");
        this.groupwareClient = Objects.requireNonNull(groupwareClient, "groupwareClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Transactional(propagation = Propagation.NEVER)
    public int runBatch() {
        List<Long> taskIds = transactionService.findPendingIds(properties.batchSize());
        int processed = 0;
        for (Long taskId : taskIds) {
            var command = transactionService.markProcessing(taskId);
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
            transactionService.markFailed(task.id(), "TASK_PAYLOAD_INVALID",
                    "Integration task payload is invalid");
            return;
        }

        try {
            groupwareClient.send(task.action(), request, task.idempotencyKey());
            transactionService.markSucceeded(task.id());
        } catch (GroupwareClientException failure) {
            transactionService.markFailed(task.id(), failure.getErrorCode(), safeMessage(failure.getErrorCode()));
        }
    }

    private static String safeMessage(String errorCode) {
        return switch (errorCode) {
            case "GROUPWARE_HTTP_ERROR" -> "Groupware returned an HTTP error";
            case "GROUPWARE_CONNECTION_ERROR" -> "Could not connect to Groupware";
            case "GROUPWARE_TIMEOUT" -> "Groupware request timed out";
            default -> "Groupware response could not be processed";
        };
    }

    private static final class InvalidTaskPayloadException extends RuntimeException {
    }
}
