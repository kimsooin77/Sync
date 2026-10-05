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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/mock/groupware/accounts")
@ConditionalOnProperty(prefix = "app.mock.groupware", name = "enabled", havingValue = "true")
public class MockGroupwareController {

    private final MockGroupwareAccountStore accountStore;

    MockGroupwareController(MockGroupwareAccountStore accountStore) {
        this.accountStore = accountStore;
    }

    @PostMapping
    ResponseEntity<Void> create(@RequestBody GroupwareAccountRequest request) {
        validateEmployeeNo(request.employeeNo(), request.employeeNo());
        accountStore.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/{employeeNo}")
    ResponseEntity<Void> update(@PathVariable String employeeNo, @RequestBody GroupwareAccountRequest request) {
        validateEmployeeNo(employeeNo, request.employeeNo());
        accountStore.update(request);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{employeeNo}/disable")
    ResponseEntity<?> disable(@PathVariable String employeeNo, @RequestBody GroupwareAccountRequest request) {
        validateEmployeeNo(employeeNo, request.employeeNo());
        if (!accountStore.disable(request)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new MockGroupwareError("ACCOUNT_NOT_FOUND", "Groupware account was not found."));
        }
        return ResponseEntity.ok().build();
    }

    private static void validateEmployeeNo(String pathEmployeeNo, String bodyEmployeeNo) {
        if (bodyEmployeeNo == null || !pathEmployeeNo.equals(bodyEmployeeNo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employee number does not match");
        }
    }
}
