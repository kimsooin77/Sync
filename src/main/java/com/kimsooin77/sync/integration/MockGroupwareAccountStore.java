package com.kimsooin77.sync.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "app.mock.groupware", name = "enabled", havingValue = "true")
public class MockGroupwareAccountStore {

    private final ConcurrentHashMap<String, GroupwareAccount> accounts = new ConcurrentHashMap<>();

    void create(GroupwareAccountRequest request) {
        GroupwareAccount existing = accounts.putIfAbsent(
                request.employeeNo(), GroupwareAccount.from(request, true));
        if (existing != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Groupware account already exists");
        }
    }

    void update(GroupwareAccountRequest request) {
        accounts.put(request.employeeNo(), GroupwareAccount.from(request, true));
    }

    boolean disable(GroupwareAccountRequest request) {
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
        return found[0];
    }

    GroupwareAccount find(String employeeNo) {
        return accounts.get(employeeNo);
    }

    void clear() {
        accounts.clear();
    }
}
