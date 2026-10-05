package com.kimsooin77.sync.simulation.groupware;

import com.kimsooin77.sync.integration.GroupwareAccountRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/mock/groupware/accounts")
@ConditionalOnProperty(prefix = "app.mock.groupware", name = "enabled", havingValue = "true")
public class MockGroupwareController {
    private final MockGroupwareAccountStore accountStore;

    public MockGroupwareController(MockGroupwareAccountStore accountStore) { this.accountStore = accountStore; }

    @PostMapping
    ResponseEntity<byte[]> create(@RequestHeader("Idempotency-Key") String key,
                                  @RequestBody GroupwareAccountRequest request) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        validatePath(request.employeeNo(), request);
        return respond(accountStore.process(parseKey(key), MockGroupwareAccountStore.Operation.CREATE,
                request.employeeNo(), request));
    }

    @PutMapping("/{employeeNo}")
    ResponseEntity<byte[]> update(@RequestHeader("Idempotency-Key") String key, @PathVariable String employeeNo,
                                  @RequestBody GroupwareAccountRequest request) {
        validatePath(employeeNo, request);
        return respond(accountStore.process(parseKey(key), MockGroupwareAccountStore.Operation.UPDATE,
                employeeNo, request));
    }

    @PatchMapping("/{employeeNo}/disable")
    ResponseEntity<byte[]> disable(@RequestHeader("Idempotency-Key") String key, @PathVariable String employeeNo,
                                   @RequestBody GroupwareAccountRequest request) {
        validatePath(employeeNo, request);
        return respond(accountStore.process(parseKey(key), MockGroupwareAccountStore.Operation.DISABLE,
                employeeNo, request));
    }

    private static UUID parseKey(String value) {
        try {
            UUID uuid = UUID.fromString(value);
            if (!uuid.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return uuid;
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must be a UUID");
        }
    }

    private static void validatePath(String pathEmployeeNo, GroupwareAccountRequest request) {
        if (request == null || request.employeeNo() == null || !pathEmployeeNo.equals(request.employeeNo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employee number does not match");
        }
    }

    private static ResponseEntity<byte[]> respond(MockGroupwareResponse response) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.statusCode());
        if (response.contentType() != null) builder.header("Content-Type", response.contentType());
        return builder.body(response.body());
    }
}
