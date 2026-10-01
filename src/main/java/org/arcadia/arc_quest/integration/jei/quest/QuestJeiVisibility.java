package org.arcadia.arc_quest.integration.jei.quest;

import org.arcadia.arc_quest.quest.api.HiddenPresentationMode;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;

/** Fail-closed visibility shared by catalog construction and focused tests. */
public final class QuestJeiVisibility {
    private QuestJeiVisibility() {}

    public static boolean canRevealPhase(PhaseDefinition phase, QuestRuntimeData runtime) {
        if (phase == null || runtime == null) return false;
        String id = phase.getPhaseId();
        if (runtime.hasCollectionData()) {
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
}
