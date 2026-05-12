package org.arcadia.arc_quest.quest.network;

import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;

import javax.annotation.Nullable;

public interface QuestCacheListener {

    default void onQuestAccepted(String questId) {}

    default void onQuestCompleted(String questId) {}

    default void onQuestFailed(String questId) {}

    default void onPhaseStarted(String questId, String phaseId) {}

    default void onQuestUpdated(String questId,
                                QuestRuntimeData newData,
                                @Nullable QuestState oldState,
                                @Nullable String oldPhaseId,
                                @Nullable QuestRuntimeData previousData) {}

    default void onObjectiveProgress(String questId, String phaseId, int objIndex,
                                     int oldProgress, int newProgress, int required) {}
}
