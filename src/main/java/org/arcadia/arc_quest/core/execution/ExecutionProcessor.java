package org.arcadia.arc_quest.core.execution;

import java.util.Objects;
import java.util.function.Function;

public interface ExecutionProcessor {

    static ExecutionProcessor createDefault() {
        return DefaultExecutionProcessor.INSTANCE;
    }

    <C, F> CoreDecision<F> decide(C context, Iterable<? extends CoreRule<C, F>> rules);

    default <C, F, R> ExecutionResult<F, R> execute(
            C context,
            Iterable<? extends CoreRule<C, F>> rules,
            Function<? super C, ? extends R> action) {
        Objects.requireNonNull(action, "action");
        CoreDecision<F> decision = decide(context, rules);
        if (!decision.allowed()) return ExecutionResult.rejected(decision.failure());
        return ExecutionResult.succeeded(action.apply(context));
    }
}
