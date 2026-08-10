package org.arcadia.arc_quest.quest.service;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.tracking.api.QuestTrackingChangeReason;
import org.arcadia.arc_quest.quest.tracking.application.QuestTrackingManager;
import org.jetbrains.annotations.Nullable;

public final class TrackedQuestService {

    private TrackedQuestService() {
    }

    public static boolean trackIfAbsent(ServerPlayer player, String questId) {
        return QuestTrackingManager.INSTANCE.onQuestAccepted(player, questId).changed();
    }

    public static boolean ensureTrackedQuest(ServerPlayer player, @Nullable String preferredQuestId) {
        return preferredQuestId != null
                ? onQuestAccepted(player, preferredQuestId)
                : onQuestTerminated(player);
    }

    public static boolean setTrackedQuest(ServerPlayer player, @Nullable String questId) {
        return questId == null || questId.isBlank()
                ? QuestTrackingManager.INSTANCE.untrack(player).changed()
                : QuestTrackingManager.INSTANCE.track(player, questId).changed();
    }

    public static boolean reconcile(ServerPlayer player, QuestTrackingChangeReason reason) {
        return QuestTrackingManager.INSTANCE.reconcile(player, reason).changed();
    }

    public static boolean onQuestAccepted(ServerPlayer player, String questId) {
        return QuestTrackingManager.INSTANCE.onQuestAccepted(player, questId).changed();
    }

    public static boolean onQuestTerminated(ServerPlayer player) {
        return QuestTrackingManager.INSTANCE.onQuestTerminated(player).changed();
    }

    public static boolean onQuestReset(ServerPlayer player) {
        return QuestTrackingManager.INSTANCE.onQuestReset(player).changed();
    }
}
