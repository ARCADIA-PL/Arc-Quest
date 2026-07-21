package org.arcadia.arc_quest.core.condition;

import org.slf4j.Logger;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class ConditionGuard {

    private ConditionGuard() {
    }

    public static boolean evaluate(BooleanSupplier evaluation,
                                   boolean failureResult,
                                   Logger logger,
                                   String context) {
        Objects.requireNonNull(evaluation, "evaluation");
        Objects.requireNonNull(logger, "logger");
        try {
            return evaluation.getAsBoolean();
        } catch (RuntimeException exception) {
            logger.warn("[ConditionGuard] Evaluation failed: {}", context, exception);
            return failureResult;
        }
    }

    public static boolean evaluateNullable(BooleanSupplier evaluation,
                                           boolean missingResult,
                                           boolean failureResult,
                                           Logger logger,
                                           String context) {
        return evaluation == null
                ? missingResult
                : evaluate(evaluation, failureResult, logger, context);
    }
}
