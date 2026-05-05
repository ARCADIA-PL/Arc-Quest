package org.arcadia.arc_quest.quest.api.rule.collection;

import org.arcadia.arc_quest.quest.api.CollectionCompletionRule;
import org.arcadia.arc_quest.quest.api.CollectionEntryConfig;
import org.arcadia.arc_quest.quest.api.CollectionRuleContext;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;

public final class CompletedEntryRatioRule implements CollectionCompletionRule {

    private final float requiredRatio;

    public CompletedEntryRatioRule(float requiredRatio) {
        this.requiredRatio = Math.max(0f, Math.min(1f, requiredRatio));
    }

    @Override
    public boolean test(CollectionRuleContext context) {
        if (requiredRatio <= 0f) return true;
        int total = 0;
        int completed = 0;
        for (String phaseId : context.getQuestDefinition().getPhaseIds()) {
            PhaseDefinition phase = context.getQuestDefinition().getPhase(phaseId);
            if (phase == null) continue;
            CollectionEntryConfig entryConfig = phase.getCollectionEntryConfig();
            if (entryConfig == null) continue;
            if (context.getCategoryId() != null && !context.getCategoryId().equals(entryConfig.getCategoryId()))
                continue;
            total++;
            if (context.getQuestRuntimeData().isPhaseCompleted(phaseId)) completed++;
        }
        return total > 0 && completed / (float) total >= requiredRatio;
    }

    @Override
    public String getDebugLabel() {
        return "CompletedEntryRatioRule(" + requiredRatio + ")";
    }
}
