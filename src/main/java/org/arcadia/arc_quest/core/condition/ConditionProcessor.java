package org.arcadia.arc_quest.core.condition;

import java.util.Collection;

public interface ConditionProcessor {

    static ConditionProcessor createDefault() {
        return new DefaultConditionProcessor();
    }

    <C> boolean evaluate(CoreCondition<? super C> condition, C context);

    <C> boolean all(Collection<? extends CoreCondition<? super C>> conditions, C context);

    <C> boolean any(Collection<? extends CoreCondition<? super C>> conditions, C context);

    <C> boolean none(CoreCondition<? super C> condition, C context);
}
