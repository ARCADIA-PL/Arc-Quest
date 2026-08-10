package org.arcadia.arc_quest.quest.tracking.api;

import org.jetbrains.annotations.Nullable;

public record QuestTrackingResult(boolean accepted,
                                  boolean changed,
                                  QuestTrackingSnapshot before,
                                  QuestTrackingSnapshot after,
                                  QuestTrackingChangeReason reason,
                                  @Nullable Rejection rejection) {

    public enum Rejection {
        INVALID_QUEST_ID,
        QUEST_NOT_ACTIVE
    }
}
