package org.arcadia.arc_quest.core.condition;

import org.arcadia.arc_quest.core.CoreProcessors;
import org.slf4j.Logger;

import java.util.function.BooleanSupplier;

public final class ConditionGuard {

    private ConditionGuard() {
    }

    public static boolean evaluate(BooleanSupplier evaluation,
                                   boolean failureResult,
                                   Logger logger,
                                   String context) {
        return CoreProcessors.get().conditions().evaluateSafely(
                evaluation, failureResult, logger, context);
    }

    public static <C> boolean evaluate(CoreCondition<? super C> condition,
                                       C conditionContext,
                                       boolean failureResult,
                                       Logger logger,
                                       String context) {
        return CoreProcessors.get().conditions().evaluateSafely(
                condition, conditionContext, failureResult, logger, context);
    }

    public static boolean evaluateNullable(BooleanSupplier evaluation,
                                           boolean missingResult,
                                           boolean failureResult,
                                           Logger logger,
                                           String context) {
        return CoreProcessors.get().conditions().evaluateNullable(
                evaluation, missingResult, failureResult, logger, context);
    }
}
