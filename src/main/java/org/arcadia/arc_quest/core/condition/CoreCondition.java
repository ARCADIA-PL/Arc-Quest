package org.arcadia.arc_quest.core.condition;

@FunctionalInterface
public interface CoreCondition<C> {

    boolean evaluate(C context);
}
