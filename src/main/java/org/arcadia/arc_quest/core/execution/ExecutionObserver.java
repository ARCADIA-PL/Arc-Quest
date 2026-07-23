package org.arcadia.arc_quest.core.execution;

public interface ExecutionObserver<C, F, R> {

    default void onRejected(C context, F failure) {
    }

    default void onSucceeded(C context, R value) {
    }

    default void onFailed(C context, F failure, RuntimeException exception) {
    }

    static <C, F, R> ExecutionObserver<C, F, R> none() {
        return new ExecutionObserver<>() {
        };
    }
}
