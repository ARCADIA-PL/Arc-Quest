package org.arcadia.arc_quest.integration.jei.quest;

import org.arcadia.arc_quest.quest.api.HiddenPresentationMode;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.data.CollectionRecordState;
import org.arcadia.arc_quest.data.sync.CollectionContentDisclosure;

/** Fail-closed visibility shared by catalog construction and focused tests. */
public final class QuestJeiVisibility {
    private QuestJeiVisibility() {}

    public static boolean canRevealPhase(PhaseDefinition phase, QuestRuntimeData runtime) {
        if (phase == null || runtime == null) return false;
        String id = phase.getPhaseId();
        if (runtime.hasCollectionData() && !phase.hasCollectionSheet()) {
            var collection = runtime.getCollectionData();
            var config = phase.getCollectionEntryConfig();
            if (!collection.isVisible(id) && !collection.isDiscovered(id)) return false;
            // A masked/placeholder card is not permission to reveal the underlying item or title.
            if (config != null && !collection.isDiscovered(id)
                    && config.getHiddenPresentationMode() != HiddenPresentationMode.FULLY_HIDDEN) return false;
            return true;
        }
        return runtime.isPhaseActive(id) || runtime.isPhaseCompleted(id);
    }

    public static boolean canRevealCollectionObjective(QuestDefinition quest, PhaseDefinition phase,
                                                      ObjectiveEntry objective, CollectionRecordState records) {
        if (!phase.hasCollectionSheet()) return true;
        boolean bound = false;
        for (var binding : phase.getCollectionSheet().getBindings()) {
            if (!binding.getObjectiveIds().contains(objective.getObjectiveId())) continue;
            bound = true;
            var entry = quest.getCollectionConfig().getEntry(binding.getEntryId());
            if (entry != null && CollectionContentDisclosure.entryRevealed(entry, records)) return true;
        }
        return !bound;
    }
}
