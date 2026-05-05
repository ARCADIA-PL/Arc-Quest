package org.arcadia.arc_quest.quest.api.rule.collection;

import org.arcadia.arc_quest.quest.api.CollectionCategoryDefinition;
import org.arcadia.arc_quest.quest.api.CollectionCompletionRule;
import org.arcadia.arc_quest.quest.api.CollectionEntryConfig;
import org.arcadia.arc_quest.quest.api.CollectionQuestConfig;
import org.arcadia.arc_quest.quest.api.CollectionRuleContext;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;

public final class CategoryCompletedRatioRule implements CollectionCompletionRule {

    private final float requiredRatio;

    public CategoryCompletedRatioRule(float requiredRatio) {
        this.requiredRatio = Math.max(0f, Math.min(1f, requiredRatio));
    }

    @Override
    public boolean test(CollectionRuleContext context) {
        if (requiredRatio <= 0f) return true;
        CollectionQuestConfig config = context.getQuestDefinition().getCollectionConfig();
        if (config == null || config.getCategories().isEmpty()) return false;
        int total = 0;
        int completed = 0;
        for (CollectionCategoryDefinition category : config.getCategories()) {
            if (!hasCategoryEntries(context, category.getCategoryId())) continue;
            total++;
            if (isCategoryComplete(context, category.getCategoryId())) completed++;
        }
        return total > 0 && completed / (float) total >= requiredRatio;
    }

    private boolean hasCategoryEntries(CollectionRuleContext context, String categoryId) {
        for (String phaseId : context.getQuestDefinition().getPhaseIds()) {
            PhaseDefinition phase = context.getQuestDefinition().getPhase(phaseId);
            if (phase == null) continue;
            CollectionEntryConfig entryConfig = phase.getCollectionEntryConfig();
            if (entryConfig != null && categoryId.equals(entryConfig.getCategoryId())) return true;
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
        return "CategoryCompletedRatioRule(" + requiredRatio + ")";
    }
}
