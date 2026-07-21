package org.arcadia.arc_quest.core.execution;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionProcessorTest {

    private final ExecutionProcessor processor = ExecutionProcessor.createDefault();

    @Test
    void decisionStopsAtFirstRejectedRule() {
        AtomicInteger evaluations = new AtomicInteger();
        List<CoreRule<String, Failure>> rules = List.of(
                CoreRule.require(context -> {
                    evaluations.incrementAndGet();
                    return context.startsWith("arc");
                }, Failure.INVALID_CONTEXT),
                CoreRule.require(context -> {
                    evaluations.incrementAndGet();
                    return false;
                }, Failure.NOT_ALLOWED),
                context -> {
                    evaluations.incrementAndGet();
                    return CoreDecision.allow();
                }
        );

        CoreDecision<Failure> decision = processor.decide("arc_quest", rules);

        assertFalse(decision.allowed());
        assertEquals(Failure.NOT_ALLOWED, decision.failure());
        assertEquals(2, evaluations.get());
    }

    @Test
    void executeRunsActionOnlyAfterAllRulesPass() {
        AtomicInteger executions = new AtomicInteger();
        ExecutionResult<Failure, Integer> success = processor.execute(
                "arc_quest",
                List.of(CoreRule.require(context -> !context.isBlank(), Failure.INVALID_CONTEXT)),
                context -> {
                    executions.incrementAndGet();
                    return context.length();
                });
        ExecutionResult<Failure, Integer> rejected = processor.execute(
                "",
                List.of(CoreRule.require(context -> !context.isBlank(), Failure.INVALID_CONTEXT)),
                context -> {
                    executions.incrementAndGet();
                    return context.length();
                });

        assertTrue(success.succeeded());
        assertEquals(9, success.value());
        assertFalse(rejected.succeeded());
        assertEquals(Failure.INVALID_CONTEXT, rejected.failure());
        assertNull(rejected.value());
        assertEquals(1, executions.get());
    }

    @Test
    void lifecycleObserverReceivesRejectedSucceededAndMappedFailureSignals() {
        AtomicInteger rejectedSignals = new AtomicInteger();
        AtomicInteger successSignals = new AtomicInteger();
        AtomicInteger failureSignals = new AtomicInteger();
        ExecutionObserver<String, Failure, Integer> observer = new ExecutionObserver<>() {
            @Override
            public void onRejected(String context, Failure failure) {
                rejectedSignals.incrementAndGet();
            }

            @Override
            public void onSucceeded(String context, Integer value) {
                successSignals.incrementAndGet();
            }

            @Override
            public void onFailed(String context, Failure failure, RuntimeException exception) {
                failureSignals.incrementAndGet();
            }
        };

        processor.execute("", List.of(
                CoreRule.require(context -> !context.isBlank(), Failure.INVALID_CONTEXT)),
                String::length, observer);
        processor.execute("arc", List.of(), String::length, observer);
        ExecutionResult<Failure, Integer> failed = processor.executeSafely(
                "arc", List.of(), context -> {
                    throw new IllegalStateException("expected");
                }, exception -> Failure.ACTION_FAILED, observer);

        assertEquals(1, rejectedSignals.get());
        assertEquals(1, successSignals.get());
        assertEquals(1, failureSignals.get());
        assertFalse(failed.succeeded());
        assertEquals(Failure.ACTION_FAILED, failed.failure());
    }

    private enum Failure {
        INVALID_CONTEXT,
        NOT_ALLOWED,
        ACTION_FAILED
    }
}
