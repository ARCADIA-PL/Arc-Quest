package org.arcadia.arc_quest.quest.tracking.api;

import org.jetbrains.annotations.Nullable;

public record QuestTrackingSnapshot(@Nullable String questId,
                                    QuestTrackingState state,
                                    long revision) {

    public QuestTrackingSnapshot {
        state = state == null ? QuestTrackingState.EMPTY : state;
        questId = questId == null || questId.isBlank() ? null : questId.trim();
        revision = Math.max(0L, revision);
        if (!state.isTracking()) questId = null;
        if (state.isTracking() && questId == null) state = QuestTrackingState.EMPTY;
    }
}
