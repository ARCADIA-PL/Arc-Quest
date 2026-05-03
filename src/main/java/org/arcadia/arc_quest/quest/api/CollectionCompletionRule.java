package org.arcadia.arc_quest.quest.api;

public interface CollectionCompletionRule {

    boolean test(CollectionRuleContext context);

    default String getDebugLabel() {
        return this.getClass().getSimpleName();
    }
}
