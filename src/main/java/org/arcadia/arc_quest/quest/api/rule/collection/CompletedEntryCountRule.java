package org.arcadia.arc_quest.quest.api.rule.collection;

import org.arcadia.arc_quest.quest.api.CollectionCompletionRule;
import org.arcadia.arc_quest.quest.api.CollectionEntryConfig;
import org.arcadia.arc_quest.quest.api.CollectionRuleContext;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;

public final class CompletedEntryCountRule implements CollectionCompletionRule {

    private final int requiredCount;

    public CompletedEntryCountRule(int requiredCount) {
        this.requiredCount = Math.max(1, requiredCount);
    }

    @Override
    public boolean test(CollectionRuleContext context) {
        int completed = 0;
        for (String phaseId : context.getQuestDefinition().getPhaseIds()) {
            PhaseDefinition phase = context.getQuestDefinition().getPhase(phaseId);
            if (phase == null) continue;
            CollectionEntryConfig entryConfig = phase.getCollectionEntryConfig();
            if (entryConfig == null) continue;
            if (context.getCategoryId() != null && !context.getCategoryId().equals(entryConfig.getCategoryId()))
                continue;
            if (context.getQuestRuntimeData().isPhaseCompleted(phaseId)) completed++;
            if (completed >= requiredCount) return true;
        }
        return false;
    }

    @Override
    public String getDebugLabel() {
        return "CompletedEntryCountRule(" + requiredCount + ")";
    }
}
