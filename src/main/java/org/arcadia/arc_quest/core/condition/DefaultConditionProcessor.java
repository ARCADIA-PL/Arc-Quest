package org.arcadia.arc_quest.core.condition;

import java.util.Collection;
import java.util.Objects;

final class DefaultConditionProcessor implements ConditionProcessor {

    @Override
    public <C> boolean evaluate(CoreCondition<? super C> condition, C context) {
        return Objects.requireNonNull(condition, "condition").evaluate(context);
    }

    @Override
    public <C> boolean all(Collection<? extends CoreCondition<? super C>> conditions, C context) {
        Objects.requireNonNull(conditions, "conditions");
        for (CoreCondition<? super C> condition : conditions) {
            if (!evaluate(condition, context)) return false;
        }
        return true;
    }

    @Override
    public <C> boolean any(Collection<? extends CoreCondition<? super C>> conditions, C context) {
        Objects.requireNonNull(conditions, "conditions");
        for (CoreCondition<? super C> condition : conditions) {
            if (evaluate(condition, context)) return true;
        }
        return false;
    }

    @Override
    public <C> boolean none(CoreCondition<? super C> condition, C context) {
        return !evaluate(condition, context);
    }
}
