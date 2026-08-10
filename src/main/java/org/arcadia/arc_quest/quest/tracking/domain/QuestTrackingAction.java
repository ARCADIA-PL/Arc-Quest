package org.arcadia.arc_quest.quest.tracking.domain;

import org.arcadia.arc_quest.quest.tracking.api.QuestTrackingChangeReason;
import org.jetbrains.annotations.Nullable;

public record QuestTrackingAction(Type type,
                                  @Nullable String questId,
                                  boolean overrideManualUntracked,
                                  boolean fallbackToFirstCandidate,
                                  QuestTrackingChangeReason reason) {

    public enum Type {
        USER_TRACK,
        USER_UNTRACK,
        AUTO_SELECT,
        RECONCILE
    }

    public static QuestTrackingAction userTrack(String questId) {
        return new QuestTrackingAction(Type.USER_TRACK, questId, true, false,
                QuestTrackingChangeReason.USER_TRACK);
    }

    public static QuestTrackingAction userUntrack() {
        return new QuestTrackingAction(Type.USER_UNTRACK, null, true, false,
                QuestTrackingChangeReason.USER_UNTRACK);
    }

    public static QuestTrackingAction autoSelect(@Nullable String preferredQuestId,
                                                 boolean overrideManualUntracked,
                                                 boolean fallbackToFirstCandidate,
                                                 QuestTrackingChangeReason reason) {
        return new QuestTrackingAction(Type.AUTO_SELECT, preferredQuestId,
                overrideManualUntracked, fallbackToFirstCandidate, reason);
    }

    public static QuestTrackingAction reconcile(QuestTrackingChangeReason reason) {
        return new QuestTrackingAction(Type.RECONCILE, null, false, true, reason);
    }
}
