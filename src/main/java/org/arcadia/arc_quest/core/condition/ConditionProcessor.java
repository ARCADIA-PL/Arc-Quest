package org.arcadia.arc_quest.core.condition;
import org.arcadia.arc_quest.util.log.ArcQuestLog;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Objects;
import java.util.function.BooleanSupplier;


public interface ConditionProcessor {

    static ConditionProcessor createDefault() {
        return new DefaultConditionProcessor();
    }

    <C> boolean evaluate(CoreCondition<? super C> condition, C context);

    <C> boolean all(Collection<? extends CoreCondition<? super C>> conditions, C context);

    <C> boolean any(Collection<? extends CoreCondition<? super C>> conditions, C context);

    <C> boolean none(CoreCondition<? super C> condition, C context);

    default boolean evaluateSafely(BooleanSupplier evaluation,
                                   boolean failureResult,
                                   Logger logger,
                                   String operation) {
        Objects.requireNonNull(evaluation, "evaluation");
        try {
            return evaluation.getAsBoolean();
        } catch (RuntimeException exception) {
            ArcQuestLog.warn(ArcQuestLog.Category.DATA, "Evaluation failed: {}", operation, exception);
            return failureResult;
        }
    }

    default <C> boolean evaluateSafely(CoreCondition<? super C> condition,
                                       C context,
                                       boolean failureResult,
                                       Logger logger,
                                       String operation) {
        Objects.requireNonNull(condition, "condition");
        return evaluateSafely(() -> evaluate(condition, context), failureResult, logger, operation);
    }

    default boolean evaluateNullable(BooleanSupplier evaluation,
                                     boolean missingResult,
                                     boolean failureResult,
                                     Logger logger,
                                     String operation) {
        return evaluation == null
                ? missingResult
                : evaluateSafely(evaluation, failureResult, logger, operation);
    }
}
