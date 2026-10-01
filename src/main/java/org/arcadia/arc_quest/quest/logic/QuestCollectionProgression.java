package org.arcadia.arc_quest.quest.logic;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.profile.CollectionEntryUpdateResult;
import org.arcadia.arc_quest.quest.logic.profile.CollectionQuestEngine;
import org.arcadia.arc_quest.quest.logic.profile.CollectionVisibilityUpdateResult;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questmarker.api.MarkTrigger;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

import static org.arcadia.arc_quest.quest.network.QuestSyncCoordinator.syncQuestStateAndPush;

/**
 * 收集任务的推进入口；将收集引擎结果转换为阶段标记和客户端同步。
 */
final class QuestCollectionProgression {
    private QuestCollectionProgression() {
    }

    static void incrementCollectionEntry(ServerPlayer player,
            String questId,
            String phaseId,
            int amount) {
        if (amount <= 0) return;

        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;
        QuestRuntimeData qdata = data.getActiveQuest(questId);
        if (qdata == null || qdata.getState() != QuestState.ACTIVE || !qdata.hasCollectionData()) return;

        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null || !def.isCollectionQuest()) return;

        boolean wasActive = qdata.isPhaseActive(phaseId);
        CollectionEntryUpdateResult result = CollectionQuestEngine.incrementEntryWithResult(player, data, def, qdata, phaseId, amount);
        if (result.isChanged()) {
            triggerCollectionPhaseChanges(player, data, qdata, def, phaseId, wasActive, result);
            syncQuestStateAndPush(player, qdata);
        }
    }

    static void revealCollectionEntry(ServerPlayer player,
            String questId,
            String phaseId) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;
        QuestRuntimeData qdata = data.getActiveQuest(questId);
        if (qdata == null || qdata.getState() != QuestState.ACTIVE || !qdata.hasCollectionData()) return;

        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null || !def.isCollectionQuest()) return;

        boolean wasActive = qdata.isPhaseActive(phaseId);
        CollectionVisibilityUpdateResult result = CollectionQuestEngine.revealEntry(player, data, def, qdata, phaseId);
        if (result.isChanged()) {
            triggerCollectionPhaseEntered(player, data, qdata, def, phaseId, wasActive);
            syncQuestStateAndPush(player, qdata);
        }
    }

    static void refreshCollectionVisibility(ServerPlayer player, String questId) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;
        QuestRuntimeData qdata = data.getActiveQuest(questId);
        if (qdata == null || qdata.getState() != QuestState.ACTIVE || !qdata.hasCollectionData()) return;

        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null || !def.isCollectionQuest()) return;

        Set<String> activeBefore = new HashSet<>(qdata.getActivePhaseIds());
        if (CollectionQuestEngine.refreshVisibility(player, data, def, qdata) > 0) {
            for (String phaseId : qdata.getActivePhaseIds()) {
                if (!activeBefore.contains(phaseId)) {
                    triggerCollectionPhaseEntered(player, data, qdata, def, phaseId, false);
                }
            }
            syncQuestStateAndPush(player, qdata);
        }
    }

    static void discoverCollectionEntry(ServerPlayer player,
            String questId,
            String phaseId) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;
        QuestRuntimeData qdata = data.getActiveQuest(questId);
        if (qdata == null || qdata.getState() != QuestState.ACTIVE || !qdata.hasCollectionData()) return;

        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null || !def.isCollectionQuest()) return;

        boolean wasActive = qdata.isPhaseActive(phaseId);
        CollectionEntryUpdateResult result = CollectionQuestEngine.discoverEntryWithResult(player, data, def, qdata, phaseId);
        if (result.isChanged()) {
            triggerCollectionPhaseChanges(player, data, qdata, def, phaseId, wasActive, result);
            syncQuestStateAndPush(player, qdata);
        }
    }

    static void addCollectionUniqueKey(ServerPlayer player,
            String questId,
            String phaseId,
            String uniqueKey) {
        if (uniqueKey == null || uniqueKey.isEmpty()) return;

        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;
        QuestRuntimeData qdata = data.getActiveQuest(questId);
        if (qdata == null || qdata.getState() != QuestState.ACTIVE || !qdata.hasCollectionData()) return;

        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null || !def.isCollectionQuest()) return;

        boolean wasActive = qdata.isPhaseActive(phaseId);
        CollectionEntryUpdateResult result = CollectionQuestEngine.addUniqueProgressWithResult(player, data, def, qdata, phaseId, uniqueKey);
        if (result.isChanged()) {
            triggerCollectionPhaseChanges(player, data, qdata, def, phaseId, wasActive, result);
            syncQuestStateAndPush(player, qdata);
        }
    }

    static void triggerCollectionPhaseChanges(ServerPlayer player, ArcQuestPlayer data,
            QuestRuntimeData runtime, QuestDefinition definition,
            String phaseId, boolean wasActive,
            CollectionEntryUpdateResult result) {
        triggerCollectionPhaseEntered(player, data, runtime, definition, phaseId, wasActive);
        if (!result.isEntryCompleted()) return;
        PhaseDefinition phase = definition.getPhase(phaseId);
        if (phase == null) return;
        QuestMarkerTriggerService.triggerPhase(
                player, data, runtime, phase, MarkTrigger.PHASE_COMPLETED);
        QuestMarkerTriggerService.triggerPhase(
                player, data, runtime, phase, MarkTrigger.PHASE_ADVANCED);
    }

    static void triggerCollectionPhaseEntered(ServerPlayer player, ArcQuestPlayer data,
            QuestRuntimeData runtime, QuestDefinition definition,
            String phaseId, boolean wasActive) {
        if (wasActive || !runtime.isPhaseActive(phaseId)) return;
        PhaseDefinition phase = definition.getPhase(phaseId);
        if (phase != null) {
            QuestMarkerTriggerService.triggerPhase(
                    player, data, runtime, phase, MarkTrigger.PHASE_ENTERED);
        }
    }
}
