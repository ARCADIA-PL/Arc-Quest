package org.arcadia.arc_quest.quest.logic.profile.collection;

import org.arcadia.arc_quest.quest.api.CollectionEntryConfig;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;

public final class CollectionCompletionEvaluator {

    private CollectionCompletionEvaluator() {
    }

    public static boolean areAllCollectionEntriesCompleted(QuestDefinition def, QuestRuntimeData runtime) {
        if (def == null || runtime == null) return false;
        int entries = 0;
        for (String phaseId : def.getPhaseIds()) {
            PhaseDefinition phase = def.getPhase(phaseId);
            if (phase == null || !phase.hasCollectionEntryConfig()) continue;
            entries++;
            if (!runtime.isPhaseCompleted(phaseId)) return false;
        }
        return entries > 0;
    }

    public static boolean isEntryCompleted(QuestRuntimeData runtime, String phaseId, CollectionEntryConfig entryConfig, int count) {
        if (runtime == null || phaseId == null || entryConfig == null) return false;
        return !runtime.isPhaseCompleted(phaseId) && count >= Math.max(1, entryConfig.getCompletionTarget());
    }
}
