package org.arcadia.arc_quest.quest.api.rule.collection;

import org.arcadia.arc_quest.quest.api.CollectionCompletionRule;
import org.arcadia.arc_quest.quest.api.CollectionEntryConfig;
import org.arcadia.arc_quest.quest.api.CollectionRuleContext;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;

public final class AllEntriesCompleteRule implements CollectionCompletionRule {

    @Override
    public boolean test(CollectionRuleContext context) {
        for (String phaseId : context.getQuestDefinition().getPhaseIds()) {
            PhaseDefinition phase = context.getQuestDefinition().getPhase(phaseId);
            if (phase == null) continue;
            CollectionEntryConfig entryConfig = phase.getCollectionEntryConfig();
            if (entryConfig == null) continue;
            if (context.getCategoryId() != null && !context.getCategoryId().equals(entryConfig.getCategoryId())) continue;
            if (!context.getQuestRuntimeData().isPhaseCompleted(phaseId)) return false;
        }
        return true;
    }
}
