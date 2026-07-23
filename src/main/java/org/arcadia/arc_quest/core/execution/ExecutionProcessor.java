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
        return execute(context, rules, action, ExecutionObserver.none());
    }

    default <C, F, R> ExecutionResult<F, R> execute(
            C context,
            Iterable<? extends CoreRule<C, F>> rules,
            Function<? super C, ? extends R> action,
            ExecutionObserver<? super C, ? super F, ? super R> observer) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(observer, "observer");
        CoreDecision<F> decision = decide(context, rules);
        if (!decision.allowed()) {
            observer.onRejected(context, decision.failure());
            return ExecutionResult.rejected(decision.failure());
        }
        R value = action.apply(context);
        observer.onSucceeded(context, value);
        return ExecutionResult.succeeded(value);
    }

    default <C, F, R> ExecutionResult<F, R> executeSafely(
            C context,
            Iterable<? extends CoreRule<C, F>> rules,
            Function<? super C, ? extends R> action,
            Function<? super RuntimeException, ? extends F> errorMapper,
            ExecutionObserver<? super C, ? super F, ? super R> observer) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(errorMapper, "errorMapper");
        Objects.requireNonNull(observer, "observer");
        CoreDecision<F> decision = decide(context, rules);
        if (!decision.allowed()) {
            observer.onRejected(context, decision.failure());
            return ExecutionResult.rejected(decision.failure());
        }
        try {
            R value = action.apply(context);
            observer.onSucceeded(context, value);
            return ExecutionResult.succeeded(value);
        } catch (RuntimeException exception) {
            F failure = Objects.requireNonNull(errorMapper.apply(exception), "mapped failure");
            observer.onFailed(context, failure, exception);
            return ExecutionResult.rejected(failure);
        }
    }
}
