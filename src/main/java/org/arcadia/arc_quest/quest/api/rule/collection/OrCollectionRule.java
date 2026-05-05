package org.arcadia.arc_quest.quest.api.rule.collection;

import org.arcadia.arc_quest.quest.api.CollectionCompletionRule;
import org.arcadia.arc_quest.quest.api.CollectionRuleContext;

import java.util.Arrays;
import java.util.List;

public final class OrCollectionRule implements CollectionCompletionRule {

    private final List<CollectionCompletionRule> rules;

    public OrCollectionRule(CollectionCompletionRule... rules) {
        this(Arrays.asList(rules));
    }

    public OrCollectionRule(List<CollectionCompletionRule> rules) {
        this.rules = List.copyOf(rules != null ? rules : List.of());
    }

    @Override
    public boolean test(CollectionRuleContext context) {
        for (CollectionCompletionRule rule : rules) {
            if (rule != null && rule.test(context)) return true;
        }
        return false;
    }

    @Override
    public String getDebugLabel() {
        return "OrCollectionRule(" + rules.size() + ")";
    }
}
