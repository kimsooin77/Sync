package com.kimsooin77.sync.sync;

import java.util.List;
import java.util.Objects;

public sealed interface NormalizationResult
        permits NormalizationResult.Success, NormalizationResult.Failure {

    record Success(NormalizedEmployee employee) implements NormalizationResult {

        public Success {
            Objects.requireNonNull(employee, "employee");
        }
    }

    record Failure(int rowNumber, String rawEmployeeNo, List<NormalizationError> errors)
            implements NormalizationResult {

        public Failure {
            if (rowNumber < 1) {
                throw new IllegalArgumentException("rowNumber must be positive");
            }
            errors = List.copyOf(errors);
            if (errors.isEmpty()) {
                throw new IllegalArgumentException("errors must not be empty");
            }
        }
    }
}
