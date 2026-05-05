package org.arcadia.arc_quest.quest.api.rule.collection;

import org.arcadia.arc_quest.quest.api.CollectionCategoryDefinition;
import org.arcadia.arc_quest.quest.api.CollectionCompletionRule;
import org.arcadia.arc_quest.quest.api.CollectionEntryConfig;
import org.arcadia.arc_quest.quest.api.CollectionQuestConfig;
import org.arcadia.arc_quest.quest.api.CollectionRuleContext;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;

public final class CategoryCompletedCountRule implements CollectionCompletionRule {

    private final int requiredCount;

    public CategoryCompletedCountRule(int requiredCount) {
        this.requiredCount = Math.max(1, requiredCount);
    }

    @Override
    public boolean test(CollectionRuleContext context) {
        CollectionQuestConfig config = context.getQuestDefinition().getCollectionConfig();
        if (config == null) return false;
        int completedCategories = 0;
        for (CollectionCategoryDefinition category : config.getCategories()) {
            if (isCategoryComplete(context, category.getCategoryId())) completedCategories++;
            if (completedCategories >= requiredCount) return true;
        }
        return false;
    }

    private boolean isCategoryComplete(CollectionRuleContext context, String categoryId) {
        boolean hasEntries = false;
        for (String phaseId : context.getQuestDefinition().getPhaseIds()) {
            PhaseDefinition phase = context.getQuestDefinition().getPhase(phaseId);
            if (phase == null) continue;
            CollectionEntryConfig entryConfig = phase.getCollectionEntryConfig();
            if (entryConfig == null || !categoryId.equals(entryConfig.getCategoryId())) continue;
            hasEntries = true;
            if (!context.getQuestRuntimeData().isPhaseCompleted(phaseId)) return false;
        }
        return hasEntries;
    }

    @Override
    public String getDebugLabel() {
        return "CategoryCompletedCountRule(" + requiredCount + ")";
    }
}
