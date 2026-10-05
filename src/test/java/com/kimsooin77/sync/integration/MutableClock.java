package com.kimsooin77.sync.integration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

final class MutableClock extends Clock {

    private final AtomicReference<Instant> instant;

    MutableClock() {
        instant = new AtomicReference<>(Instant.now());
    }

    void reset() {
        instant.set(Instant.now());
    }

    void advanceSeconds(long seconds) {
        instant.updateAndGet(value -> value.plusSeconds(seconds));
    }

    void advanceMillis(long millis) {
        instant.updateAndGet(value -> value.plusMillis(millis));
    }

    @Override
    public ZoneId getZone() {
        return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
        if (!Objects.requireNonNull(zone, "zone").equals(getZone())) {
            throw new IllegalArgumentException("MutableClock only supports UTC");
        }
        return this;
    }

    @Override
    public Instant instant() {
        return instant.get();
    }
}
