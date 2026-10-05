package com.kimsooin77.sync.simulation.groupware;

import com.kimsooin77.sync.integration.GroupwareAccount;
import com.kimsooin77.sync.integration.GroupwareAccountRequest;
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
    private final ConcurrentHashMap<UUID, IdempotencyState> keyStates = new ConcurrentHashMap<>();
    private final FailureSimulation failureSimulation;
    private final MockGroupwareProperties properties;

    public MockGroupwareAccountStore(FailureSimulation failureSimulation, MockGroupwareProperties properties) {
        this.failureSimulation = failureSimulation;
        this.properties = properties;
    }

    MockGroupwareResponse process(UUID key, Operation operation, String pathEmployeeNo,
                                  GroupwareAccountRequest request) {
        RequestFingerprint fingerprint = new RequestFingerprint(operation, pathEmployeeNo, request.employeeNo(),
                request.name(), request.email(), request.departmentCode(), request.employmentStatus());
        IdempotencyState state = keyStates.computeIfAbsent(key, ignored -> new IdempotencyState());
        synchronized (state) {
            if (state.fingerprint != null && !state.fingerprint.equals(fingerprint)) {
                return json(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT",
                        "Idempotency key was already used for a different request.");
            }
            if (state.fingerprint == null) state.fingerprint = fingerprint;
            if (state.successfulResponse != null) return state.successfulResponse;

            FailureRule rule = failureSimulation.forEmployee(pathEmployeeNo);
            switch (rule.mode()) {
                case HTTP_500 -> { return http500(); }
                case TIMEOUT -> { sleep(properties.getTimeoutDelayMs()); return http500(); }
                case DELAY -> sleep(rule.delayMs());
                case FAIL_ONCE_THEN_SUCCESS -> {
                    if (!state.firstFailureConsumed) {
                        state.firstFailureConsumed = true;
                        return http500();
                    }
                }
                case NORMAL -> { }
            }
            MockGroupwareResponse result = execute(operation, request);
            if (isBusinessSuccess(operation, result)) state.successfulResponse = result;
            return result;
        }
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
        return existing == null ? empty(HttpStatus.CREATED)
                : json(HttpStatus.CONFLICT, "ACCOUNT_ALREADY_EXISTS", "Groupware account already exists.");
    }

    private MockGroupwareResponse update(GroupwareAccountRequest request) {
        accounts.put(request.employeeNo(), GroupwareAccount.from(request, true));
        return empty(HttpStatus.OK);
    }

    private MockGroupwareResponse disable(GroupwareAccountRequest request) {
        boolean[] found = {false};
        accounts.compute(request.employeeNo(), (employeeNo, existing) -> {
            if (existing == null) return null;
            found[0] = true;
            return existing.enabled() ? GroupwareAccount.from(request, false) : existing;
        });
        return found[0] ? empty(HttpStatus.OK)
                : json(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Groupware account was not found.");
    }

    private static boolean isBusinessSuccess(Operation operation, MockGroupwareResponse response) {
        return response.statusCode() >= 200 && response.statusCode() < 300
                || operation == Operation.DISABLE && response.statusCode() == 404
                && new String(response.body(), StandardCharsets.UTF_8).contains("\"code\":\"ACCOUNT_NOT_FOUND\"");
    }

    private static void sleep(long delayMs) {
        try { Thread.sleep(delayMs); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }

    private static MockGroupwareResponse http500() {
        return new MockGroupwareResponse(500, "simulated failure".getBytes(StandardCharsets.UTF_8), "text/plain");
    }

    private static MockGroupwareResponse empty(HttpStatus status) {
        return new MockGroupwareResponse(status.value(), new byte[0], null);
    }

    private static MockGroupwareResponse json(HttpStatus status, String code, String message) {
        String body = "{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}";
        return new MockGroupwareResponse(status.value(), body.getBytes(StandardCharsets.UTF_8), "application/json");
    }

    public GroupwareAccount find(String employeeNo) { return accounts.get(employeeNo); }

    public void clear() {
        accounts.clear();
        keyStates.clear();
        failureSimulation.clearOverrides();
    }

    enum Operation { CREATE, UPDATE, DISABLE }

    private static final class IdempotencyState {
        private RequestFingerprint fingerprint;
        private boolean firstFailureConsumed;
        private MockGroupwareResponse successfulResponse;
    }

    private record RequestFingerprint(Operation operation, String pathEmployeeNo, String bodyEmployeeNo,
                                      String name, String email, String departmentCode, String employmentStatus) { }
}
