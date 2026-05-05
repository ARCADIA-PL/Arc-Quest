package org.arcadia.arc_quest.quest.api.rule.collection;

import org.arcadia.arc_quest.quest.api.CollectionCompletionRule;
import org.arcadia.arc_quest.quest.api.CollectionRuleContext;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionCategoryStateResolver;

public final class CategoryCompletedRatioRule implements CollectionCompletionRule {

    private final float requiredRatio;

    public CategoryCompletedRatioRule(float requiredRatio) {
        this.requiredRatio = Math.max(0f, Math.min(1f, requiredRatio));
    }

    @Override
    public boolean test(CollectionRuleContext context) {
        if (requiredRatio <= 0f) return true;
        return CollectionCategoryStateResolver.completedCategoryRatio(context) >= requiredRatio;
    }

    @Override
    public String getDebugLabel() {
        return "CategoryCompletedRatioRule(" + requiredRatio + ")";
    }
}
