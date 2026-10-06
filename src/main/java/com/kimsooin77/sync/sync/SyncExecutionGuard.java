package com.kimsooin77.sync.sync;

import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Component
public class SyncExecutionGuard {

    private final AtomicBoolean running = new AtomicBoolean();

    public <T> T execute(Supplier<T> operation) {
        Objects.requireNonNull(operation, "operation");
        if (!running.compareAndSet(false, true)) {
            throw new SyncAlreadyRunningException();
        }
        try {
            return operation.get();
        } finally {
            running.set(false);
        }
    }
}
