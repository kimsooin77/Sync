package com.kimsooin77.sync.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/mock/groupware/accounts")
@ConditionalOnProperty(prefix = "app.mock.groupware", name = "enabled", havingValue = "true")
public class MockGroupwareController {

    private final MockGroupwareAccountStore accountStore;

    MockGroupwareController(MockGroupwareAccountStore accountStore) {
        this.accountStore = accountStore;
    }

    @PostMapping
    ResponseEntity<byte[]> create(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                  @RequestBody GroupwareAccountRequest request) {
        validateEmployeeNo(request.employeeNo(), request.employeeNo());
        return response(accountStore.process(parseKey(idempotencyKey), MockGroupwareAccountStore.Operation.CREATE,
                request.employeeNo(), request));
    }

    @PutMapping("/{employeeNo}")
    ResponseEntity<byte[]> update(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                  @PathVariable String employeeNo, @RequestBody GroupwareAccountRequest request) {
        validateEmployeeNo(employeeNo, request.employeeNo());
        return response(accountStore.process(parseKey(idempotencyKey), MockGroupwareAccountStore.Operation.UPDATE,
                employeeNo, request));
    }

    @PatchMapping("/{employeeNo}/disable")
    ResponseEntity<byte[]> disable(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                   @PathVariable String employeeNo, @RequestBody GroupwareAccountRequest request) {
        validateEmployeeNo(employeeNo, request.employeeNo());
        return response(accountStore.process(parseKey(idempotencyKey), MockGroupwareAccountStore.Operation.DISABLE,
                employeeNo, request));
    }

    private static UUID parseKey(String value) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException("Idempotency-Key must use canonical UUID format");
            }
            return parsed;
        } catch (IllegalArgumentException invalidKey) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must be a UUID");
        }
    }

    private static ResponseEntity<byte[]> response(MockGroupwareResponse response) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.statusCode());
        if (response.contentType() != null) {
            builder.header("Content-Type", response.contentType());
        }
        return builder.body(response.body());
    }

    private static void validateEmployeeNo(String pathEmployeeNo, String bodyEmployeeNo) {
        if (bodyEmployeeNo == null || !pathEmployeeNo.equals(bodyEmployeeNo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employee number does not match");
        }
    }
}
