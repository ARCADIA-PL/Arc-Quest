package com.example.arcqaddon;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import org.arcadia.arc_quest.quest.network.QuestRejectCodeDictionary;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

import java.util.UUID;

public final class ScenarioQuestProgressAdapter {
    private ScenarioQuestProgressAdapter() {
    }

    public static QuestRejectCodeDictionary.Code ensureAccepted(
            ServerPlayer player, ResourceLocation questId) {
        String id = questId.toString();
        ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
        if (data.isQuestActive(id)) {
            return QuestRejectCodeDictionary.Code.OK;
        }
        if (data.isQuestCompleted(id)) {
            return QuestRejectCodeDictionary.Code.ALREADY_COMPLETED_NOT_REPEATABLE;
        }
        return QuestProgressHandler.acceptQuestWithCode(player, id);
    }

    public static boolean incrementActiveObjective(ServerPlayer player,
                                                   ResourceLocation questId,
                                                   String phaseId,
                                                   int objectiveIndex,
                                                   int amount) {
        if (amount <= 0) {
            return false;
        }
        QuestRuntimeData runtime = ArcQuestPlayerManager.getOrCreate(player)
                .getActiveQuest(questId.toString());
        if (runtime == null || !runtime.isPhaseActive(phaseId)) {
            return false;
        }
        QuestProgressHandler.incrementObjective(
                player, questId.toString(), phaseId, objectiveIndex, amount);
        return true;
    }

    public static QuestRejectCodeDictionary.Code completePhase(
            ServerPlayer player, ResourceLocation questId, String phaseId) {
        return QuestProgressHandler.forceCompletePhaseResult(
                player, questId.toString(), phaseId);
    }

    public static void completePhaseFromAsync(MinecraftServer server,
                                              UUID playerId,
                                              ResourceLocation questId,
                                              String phaseId) {
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                completePhase(player, questId, phaseId);
            }
        });
    }
}
