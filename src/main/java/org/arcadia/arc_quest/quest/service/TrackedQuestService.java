package org.arcadia.arc_quest.quest.service;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import org.arcadia.arc_quest.api.event.quest.TrackedQuestChangedEvent;
import org.arcadia.arc_quest.quest.network.QuestSyncCoordinator;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.jetbrains.annotations.Nullable;

public final class TrackedQuestService {

    private TrackedQuestService() {
    }

    public static boolean setTrackedQuest(ServerPlayer player, @Nullable String questId) {
        ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
        String normalized = normalize(questId);
        if (normalized != null) {
            if (ResourceLocation.tryParse(normalized) == null || !data.isQuestActive(normalized)) return false;
        }

        String oldQuestId = data.getTrackedQuestId();
        if (!data.setTrackedQuestId(normalized)) return false;

        MinecraftForge.EVENT_BUS.post(new TrackedQuestChangedEvent(
                player.serverLevel(), player, oldQuestId, normalized));
        QuestSyncCoordinator.persistAndSyncIfChanged(player, data);
        return true;
    }

    @Nullable
    private static String normalize(@Nullable String questId) {
        return questId == null || questId.isBlank() ? null : questId.trim();
    }
}
