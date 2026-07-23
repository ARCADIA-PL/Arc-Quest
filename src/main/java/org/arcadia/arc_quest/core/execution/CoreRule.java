package org.arcadia.arc_quest.core.execution;

import java.util.Objects;
import java.util.function.Predicate;

@FunctionalInterface
public interface CoreRule<C, F> {

    CoreDecision<F> evaluate(C context);

    static <C, F> CoreRule<C, F> require(Predicate<? super C> requirement, F failure) {
        Objects.requireNonNull(requirement, "requirement");
        Objects.requireNonNull(failure, "failure");
        return context -> requirement.test(context)
                ? CoreDecision.allow()
                : CoreDecision.reject(failure);
    }
}
