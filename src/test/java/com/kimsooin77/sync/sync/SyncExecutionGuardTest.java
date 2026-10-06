package com.kimsooin77.sync.sync;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyncExecutionGuardTest {

    private final SyncExecutionGuard guard = new SyncExecutionGuard();

    @Test
    void rejectsASecondExecutionImmediatelyWhileFirstIsRunning() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread first = new Thread(() -> {
            try {
                guard.execute(() -> {
                    entered.countDown();
                    await(finish);
                    return null;
                });
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        first.start();

        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> guard.execute(() -> "second"))
                .isInstanceOf(SyncAlreadyRunningException.class);
        finish.countDown();
        first.join(2_000);

        assertThat(first.isAlive()).isFalse();
        assertThat(failure.get()).isNull();
        assertThat(guard.execute(() -> "next")).isEqualTo("next");
    }

    @Test
    void releasesGuardWhenOperationThrows() {
        assertThatThrownBy(() -> guard.execute(() -> {
            throw new IllegalStateException("expected failure");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(guard.execute(() -> "next")).isEqualTo("next");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("test did not release guarded operation");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
}
