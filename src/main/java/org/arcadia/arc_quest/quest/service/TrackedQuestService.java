package org.arcadia.arc_quest.quest.service;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import org.arcadia.arc_quest.api.event.quest.TrackedQuestChangedEvent;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.QuestSyncCoordinator;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

public final class TrackedQuestService {

    private TrackedQuestService() {
    }

    public static boolean trackIfAbsent(ServerPlayer player, String questId) {
        ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
        if (isTrackable(data, data.getTrackedQuestId())) return false;
        return ensureTrackedQuest(player, questId);
    }

    public static boolean ensureTrackedQuest(ServerPlayer player, @Nullable String preferredQuestId) {
        ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
        String currentQuestId = normalize(data.getTrackedQuestId());
        if (isTrackable(data, currentQuestId)) return false;

        String candidateQuestId = normalize(preferredQuestId);
        if (candidateQuestId == null || !isTrackable(data, candidateQuestId)) {
            candidateQuestId = data.getAllActiveQuests().keySet().stream()
                    .filter(id -> isTrackable(data, id))
                    .min(Comparator.<String>comparingLong(id -> data.getActiveQuest(id).getAcceptedAtTick())
                            .thenComparing(Comparator.naturalOrder()))
                    .orElse(null);
        }
        return setTrackedQuest(player, candidateQuestId);
    }

    public static boolean setTrackedQuest(ServerPlayer player, @Nullable String questId) {
        ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
        String normalized = normalize(questId);
        if (normalized != null) {
            if (ResourceLocation.tryParse(normalized) == null || !isTrackable(data, normalized)) return false;
        }

        String oldQuestId = data.getTrackedQuestId();
        if (!data.setTrackedQuestId(normalized)) return false;

        NeoForge.EVENT_BUS.post(new TrackedQuestChangedEvent(
                player.serverLevel(), player, oldQuestId, normalized));
        QuestSyncCoordinator.persistAndSyncIfChanged(player, data);
        return true;
    }

    private static boolean isTrackable(ArcQuestPlayer data, @Nullable String questId) {
        if (questId == null) return false;
        QuestRuntimeData runtime = data.getActiveQuest(questId);
        return runtime != null && runtime.getState() == QuestState.ACTIVE;
    }

    @Nullable
    private static String normalize(@Nullable String questId) {
        return questId == null || questId.isBlank() ? null : questId.trim();
    }
}
