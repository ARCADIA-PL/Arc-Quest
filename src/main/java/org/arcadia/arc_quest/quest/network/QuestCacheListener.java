package org.arcadia.arc_quest.quest.network;

import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;

import javax.annotation.Nullable;
import java.util.Map;

public interface QuestCacheListener {

    default void onQuestAccepted(String questId) {}

    default void onQuestCompleted(String questId) {}

    default void onQuestFailed(String questId) {}

    default void onPhaseStarted(String questId, String phaseId) {}

    /** Authoritative replacement; restore pending work without replaying old events. */
    default void onFullSync(Map<String, QuestRuntimeData> activeQuests) {}

    default void onCacheCleared() {}

    default void onFlagsAndVariablesUpdated() {}
    default void onCollectionEntriesDiscovered(java.util.Set<net.minecraft.resources.ResourceLocation> entries) {}

    default void onTrackedPhaseFocusChanged(String questId, @Nullable String oldPhaseId, String newPhaseId) {}

    default void onQuestUpdated(String questId,
                                QuestRuntimeData newData,
                                @Nullable QuestState oldState,
                                @Nullable String oldPhaseId,
                                @Nullable QuestRuntimeData previousData) {}

    default void onObjectiveProgress(String questId, String phaseId, int objIndex,
                                     int oldProgress, int newProgress, int required) {}

    default void onObjectiveProgress(String questId, String phaseId, int objIndex,
                                     int oldProgress, int newProgress, int oldRequired, int required) {
        onObjectiveProgress(questId, phaseId, objIndex, oldProgress, newProgress, required);
    }
}
