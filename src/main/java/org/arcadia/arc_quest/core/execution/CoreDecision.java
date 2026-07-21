package org.arcadia.arc_quest.core.execution;

import java.util.Objects;
import java.util.Optional;

public record CoreDecision<F>(boolean allowed, F failure) {

    public CoreDecision {
        if (allowed && failure != null) {
            throw new IllegalArgumentException("Allowed decision cannot contain a failure");
        }
        if (!allowed) {
            Objects.requireNonNull(failure, "failure");
        }
    }

    public static <F> CoreDecision<F> allow() {
        return new CoreDecision<>(true, null);
    }

    public static <F> CoreDecision<F> reject(F failure) {
        return new CoreDecision<>(false, Objects.requireNonNull(failure, "failure"));
    }

    public Optional<F> failureOptional() {
        return Optional.ofNullable(failure);
    }
}
