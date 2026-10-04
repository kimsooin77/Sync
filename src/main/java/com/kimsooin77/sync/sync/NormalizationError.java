package com.kimsooin77.sync.sync;

import java.util.Objects;

public record NormalizationError(NormalizationErrorCode code, String message) {

    public NormalizationError {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}
