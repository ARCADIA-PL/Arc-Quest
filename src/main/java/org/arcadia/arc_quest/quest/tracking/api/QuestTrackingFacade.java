package org.arcadia.arc_quest.quest.tracking.api;

import net.minecraft.server.level.ServerPlayer;

public interface QuestTrackingFacade {

    QuestTrackingResult track(ServerPlayer player, String questId);

    QuestTrackingResult untrack(ServerPlayer player);

    QuestTrackingResult onQuestAccepted(ServerPlayer player, String questId);

    QuestTrackingResult onQuestTerminated(ServerPlayer player);

    QuestTrackingResult onQuestReset(ServerPlayer player);

    QuestTrackingResult reconcile(ServerPlayer player, QuestTrackingChangeReason reason);

    QuestTrackingSnapshot snapshot(ServerPlayer player);
}
