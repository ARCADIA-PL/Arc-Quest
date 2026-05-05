package org.arcadia.arc_quest.quest.api.rule.collection;

import org.arcadia.arc_quest.quest.api.CollectionCompletionRule;
import org.arcadia.arc_quest.quest.api.CollectionRuleContext;

public final class NotCollectionRule implements CollectionCompletionRule {

    private final CollectionCompletionRule rule;

    public NotCollectionRule(CollectionCompletionRule rule) {
        this.rule = rule;
    }

    @Override
    public boolean test(CollectionRuleContext context) {
        return rule == null || !rule.test(context);
    }

    @Override
    public String getDebugLabel() {
        return "NotCollectionRule(" + (rule != null ? rule.getDebugLabel() : "null") + ")";
    }
}
