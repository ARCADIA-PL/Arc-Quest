package com.example.arcqaddon;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.QuestRejectCodeDictionary;
import org.arcadia.arc_quest.quest.service.TrackedQuestService;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

import java.util.Set;

public final class ScenarioPlayerStateFacade {
    private ScenarioPlayerStateFacade() {
    }

    public static boolean isQuestActive(ServerPlayer player, ResourceLocation questId) {
        return ArcQuestPlayerManager.getOrCreate(player).isQuestActive(questId.toString());
    }

    public static boolean isPhaseActive(ServerPlayer player,
                                        ResourceLocation questId,
                                        String phaseId) {
        QuestRuntimeData runtime = ArcQuestPlayerManager.getOrCreate(player)
                .getActiveQuest(questId.toString());
        return runtime != null && runtime.isPhaseActive(phaseId);
    }

    public static Set<String> activePhases(ServerPlayer player, ResourceLocation questId) {
        QuestRuntimeData runtime = ArcQuestPlayerManager.getOrCreate(player)
                .getActiveQuest(questId.toString());
        return runtime == null ? Set.of() : Set.copyOf(runtime.getActivePhaseIds());
    }

    public static QuestRejectCodeDictionary.Code ensureAccepted(
            ServerPlayer player, ResourceLocation questId) {
        return ScenarioQuestProgressAdapter.ensureAccepted(player, questId);
    }

    public static boolean trackActiveQuest(ServerPlayer player, ResourceLocation questId) {
        ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
        return data.isQuestActive(questId.toString())
                && TrackedQuestService.setTrackedQuest(player, questId.toString());
    }
}
