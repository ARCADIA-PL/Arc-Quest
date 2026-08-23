package org.arcadia.arc_quest.quest.tracking.application;

import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class TrackedPhaseFocusService {

    private TrackedPhaseFocusService() {
    }

    @Nullable
    public static String resolve(ArcQuestPlayer data,
                                 @Nullable String trackedQuestId,
                                 @Nullable QuestRuntimeData runtime,
                                 @Nullable QuestDefinition definition) {
        if (trackedQuestId == null || runtime == null || runtime.getState() != QuestState.ACTIVE
                || definition == null || !trackedQuestId.equals(runtime.getQuestId())
                || !Objects.equals(data.getTrackedQuestId(), trackedQuestId)) {
            data.setTrackedPhaseId(null);
            return null;
        }

        String persistedPhaseId = data.getTrackedPhaseId();
        if (persistedPhaseId != null && definition.getPhase(persistedPhaseId) != null
                && runtime.isPhaseActive(persistedPhaseId)) {
            return persistedPhaseId;
        }

        String fallbackPhaseId = definition.getPhaseIds().stream()
                .filter(runtime::isPhaseActive)
                .findFirst()
                .orElse(null);
        data.setTrackedPhaseId(fallbackPhaseId);
        return fallbackPhaseId;
    }
}
