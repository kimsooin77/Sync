package com.kimsooin77.sync.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "app.mock.groupware", name = "enabled", havingValue = "true")
public class MockGroupwareAccountStore {

    private final ConcurrentHashMap<String, GroupwareAccount> accounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ProcessedRequest> processedRequests = new ConcurrentHashMap<>();

    MockGroupwareResponse process(UUID idempotencyKey, Operation operation, String employeeNo,
                                  GroupwareAccountRequest request) {
        RequestIdentity identity = new RequestIdentity(operation, employeeNo, request);
        java.util.concurrent.atomic.AtomicReference<MockGroupwareResponse> response =
                new java.util.concurrent.atomic.AtomicReference<>();
        processedRequests.compute(idempotencyKey, (key, previous) -> {
            if (previous != null) {
                if (previous.identity().equals(identity)) {
                    response.set(previous.response());
                } else {
                    response.set(json(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT",
                            "Idempotency key was already used for a different request."));
                }
                return previous;
            }

            MockGroupwareResponse outcome = execute(operation, request);
            response.set(outcome);
            return isBusinessSuccess(operation, outcome) ? new ProcessedRequest(identity, outcome) : null;
        });
        return response.get();
    }

    private MockGroupwareResponse execute(Operation operation, GroupwareAccountRequest request) {
        return switch (operation) {
            case CREATE -> create(request);
            case UPDATE -> update(request);
            case DISABLE -> disable(request);
        };
    }

    private MockGroupwareResponse create(GroupwareAccountRequest request) {
        GroupwareAccount existing = accounts.putIfAbsent(request.employeeNo(), GroupwareAccount.from(request, true));
        return existing == null
                ? empty(HttpStatus.CREATED)
                : json(HttpStatus.CONFLICT, "ACCOUNT_ALREADY_EXISTS", "Groupware account already exists.");
    }

    private MockGroupwareResponse update(GroupwareAccountRequest request) {
        accounts.put(request.employeeNo(), GroupwareAccount.from(request, true));
        return empty(HttpStatus.OK);
    }

    private MockGroupwareResponse disable(GroupwareAccountRequest request) {
        boolean[] found = {false};
        accounts.compute(request.employeeNo(), (employeeNo, existing) -> {
            if (existing == null) {
                return null;
            }
            found[0] = true;
            if (!existing.enabled()) {
                return existing;
            }
            return GroupwareAccount.from(request, false);
        });
        return found[0]
                ? empty(HttpStatus.OK)
                : json(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Groupware account was not found.");
    }

    private static boolean isBusinessSuccess(Operation operation, MockGroupwareResponse response) {
        return response.statusCode() >= 200 && response.statusCode() < 300
                || operation == Operation.DISABLE && response.statusCode() == 404
                && new String(response.body(), StandardCharsets.UTF_8)
                .contains("\"code\":\"ACCOUNT_NOT_FOUND\"");
    }

    private static MockGroupwareResponse empty(HttpStatus status) {
        return new MockGroupwareResponse(status.value(), new byte[0], null);
    }

    private static MockGroupwareResponse json(HttpStatus status, String code, String message) {
        String body = "{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}";
        return new MockGroupwareResponse(status.value(), body.getBytes(StandardCharsets.UTF_8), "application/json");
    }

    GroupwareAccount find(String employeeNo) {
        return accounts.get(employeeNo);
    }

    void clear() {
        accounts.clear();
        processedRequests.clear();
    }

    enum Operation { CREATE, UPDATE, DISABLE }

    private record RequestIdentity(Operation operation, String employeeNo, GroupwareAccountRequest request) { }

    private record ProcessedRequest(RequestIdentity identity, MockGroupwareResponse response) { }
}
