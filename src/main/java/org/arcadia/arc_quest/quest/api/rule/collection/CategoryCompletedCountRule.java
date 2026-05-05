package org.arcadia.arc_quest.quest.api.rule.collection;

import org.arcadia.arc_quest.quest.api.CollectionCompletionRule;
import org.arcadia.arc_quest.quest.api.CollectionRuleContext;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionCategoryStateResolver;

public final class CategoryCompletedCountRule implements CollectionCompletionRule {

    private final int requiredCount;

    public CategoryCompletedCountRule(int requiredCount) {
        this.requiredCount = Math.max(1, requiredCount);
    }

    @Override
    public boolean test(CollectionRuleContext context) {
        return CollectionCategoryStateResolver.countCompletedCategories(context) >= requiredCount;
    }

    @Override
    public String getDebugLabel() {
        return "CategoryCompletedCountRule(" + requiredCount + ")";
    }
}
