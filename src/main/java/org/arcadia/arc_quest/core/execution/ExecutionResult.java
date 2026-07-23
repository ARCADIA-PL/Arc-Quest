package org.arcadia.arc_quest.core.execution;

import java.util.Objects;
import java.util.Optional;

public record ExecutionResult<F, R>(boolean succeeded, F failure, R value) {

    public ExecutionResult {
        if (succeeded && failure != null) {
            throw new IllegalArgumentException("Successful result cannot contain a failure");
        }
        if (!succeeded) {
            Objects.requireNonNull(failure, "failure");
        }
    }

    public static <F, R> ExecutionResult<F, R> succeeded(R value) {
        return new ExecutionResult<>(true, null, value);
    }

    public static <F, R> ExecutionResult<F, R> rejected(F failure) {
        return new ExecutionResult<>(false, Objects.requireNonNull(failure, "failure"), null);
    }

    public Optional<F> failureOptional() {
        return Optional.ofNullable(failure);
    }

    public Optional<R> valueOptional() {
        return Optional.ofNullable(value);
    }
}
