package org.arcadia.arc_quest.core.condition;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionGuardTest {

    @Test
    void returnsConditionResultWhenEvaluationSucceeds() {
        assertTrue(ConditionGuard.evaluate(
                () -> true, false, LoggerFactory.getLogger(getClass()), "success"));
        assertFalse(ConditionGuard.evaluate(
                () -> false, true, LoggerFactory.getLogger(getClass()), "success"));
    }

    @Test
    void returnsFailureResultWhenEvaluationThrows() {
        assertTrue(ConditionGuard.evaluate(
                () -> {
                    throw new IllegalStateException("failure");
                }, true, LoggerFactory.getLogger(getClass()), "failure"));
    }

    @Test
    void nullableEvaluationUsesMissingResult() {
        assertTrue(ConditionGuard.evaluateNullable(
                null, true, false, LoggerFactory.getLogger(getClass()), "missing"));
        assertFalse(ConditionGuard.evaluateNullable(
                null, false, true, LoggerFactory.getLogger(getClass()), "missing"));
    }
}
