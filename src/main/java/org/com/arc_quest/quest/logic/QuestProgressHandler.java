package org.com.arc_quest.quest.logic;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import org.com.arc_quest.api.event.*;
import org.com.arc_quest.quest.api.*;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.com.arc_quest.quest.capability.QuestRuntimeData;
import org.com.arc_quest.quest.event.QuestChangeEvent;
import org.com.arc_quest.quest.event.QuestEventBus;
import org.com.arc_quest.quest.network.QuestRejectCodeDictionary;
import org.com.arc_quest.quest.network.QuestSyncCoordinator;
import org.com.arc_quest.quest.registry.QuestRegistry;
import org.com.arc_quest.quest.tracking.ObjectiveTracker;
import org.com.arc_quest.quest.tracking.TrackedObjective;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 任务进度推进核心逻辑（服务端）。
 * 并行模型：同一 Quest 内可有多个 active phase 同时推进。
 */
public final class QuestProgressHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private QuestProgressHandler() {
    }

    // ═══════════════════════════════════════════════════════
    //  接受任务
    // ═══════════════════════════════════════════════════════

    public static boolean acceptQuest(ServerPlayer player, String questId) {
        return acceptQuestWithCode(player, questId) == QuestRejectCodeDictionary.Code.OK;
    }

    private static boolean shouldCompleteQuest(QuestDefinition def, QuestRuntimeData data) {
        int done = data.getCompletedPhaseIds().size();
        return switch (def.getCompletionPolicy()) {
            case ALL -> done >= def.getPhaseIds().size();
            case ANY -> done >= 1;
            case N_OF_M -> done >= Math.max(1, def.getCompletionRequiredCount());
            case SPECIFIC_PHASE -> {
                String target = def.getCompletionTargetPhaseId();
                yield target != null && data.getCompletedPhaseIds().contains(target);
            }
        };
    }

    public static QuestRejectCodeDictionary.Code acceptQuestWithCode(ServerPlayer player, String questId) {
        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) {
            LOGGER.warn("[ArcQuest] Cannot accept unknown quest: {}", questId);
            return QuestRejectCodeDictionary.Code.QUEST_NOT_FOUND;
        }

        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);

        if (cap.isQuestActive(questId)) {
            return QuestRejectCodeDictionary.Code.ALREADY_ACTIVE;
        }
        if (cap.isQuestCompleted(questId) && !def.isRepeatable()) {
            return QuestRejectCodeDictionary.Code.ALREADY_COMPLETED_NOT_REPEATABLE;
        }

        Set<ResourceLocation> completedQuests = cap.getCompletedQuestLocations();
        for (ICondition cond : def.getUnlockConditions()) {
            if (!cond.test(player, completedQuests, cap.getAllFlags(), cap.getAllVariables())) {
                return QuestRejectCodeDictionary.Code.UNLOCK_CONDITION_NOT_MET;
            }
        }

        PhaseDefinition firstPhase = def.getInitialPhase();
        if (firstPhase == null) {
            return QuestRejectCodeDictionary.Code.NO_INITIAL_PHASE;
        }

        QuestRuntimeData data = new QuestRuntimeData(
                questId,
                firstPhase.getPhaseId(),
                firstPhase.getObjectives().size(),
                player.getServer() != null ? player.getServer().getTickCount() : 0
        );
        cap.addActiveQuest(data);

        for (String flag : def.getFlagsToSetOnAccept()) {
            cap.setFlag(flag);
        }

        registerPhaseObjectives(player, def, firstPhase);

        syncQuestStateAndPush(player, data);
        syncFlagsVarsAndPush(player, cap);
        QuestEventBus.fire(QuestChangeEvent.questAccepted(ResourceLocation.parse(questId)));

        MinecraftForge.EVENT_BUS.post(new QuestAcceptedEvent(player, ResourceLocation.parse(questId)));
        MinecraftForge.EVENT_BUS.post(new QuestStartedEvent(player, ResourceLocation.parse(questId)));

        return QuestRejectCodeDictionary.Code.OK;
    }

    // ═══════════════════════════════════════════════════════
    //  目标推进（并行 phase 维度）
    // ═══════════════════════════════════════════════════════

    public static void incrementObjective(ServerPlayer player,
                                          String questId,
                                          String phaseId,
                                          int objIndex,
                                          int amount) {
        if (amount <= 0) return;

        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data == null || data.getState() != QuestState.ACTIVE) return;
        if (!data.isPhaseActive(phaseId)) return;

        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) return;

        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase == null) return;
        if (objIndex < 0 || objIndex >= phase.getObjectives().size()) return;

        ObjectiveEntry objEntry = phase.getObjectives().get(objIndex);
        int required = objEntry.getRequiredCount();

        int currentProgress = data.getObjectiveProgress(phaseId, objIndex);
        if (currentProgress >= required) return;

        int newProgress = data.incrementProgress(phaseId, objIndex, amount, required);

        syncDeltaProgressAndPush(player, questId, phaseId, objIndex, newProgress);

        QuestEventBus.fire(QuestChangeEvent.objectiveProgressed(
                ResourceLocation.parse(questId), objIndex, newProgress, required));

        MinecraftForge.EVENT_BUS.post(new QuestProgressChangedEvent(
                player, ResourceLocation.parse(questId), phaseId,
                objIndex, currentProgress, newProgress, required));

        checkPhaseCompletion(player, cap, data, def, phaseId);
    }

    private static void checkPhaseCompletion(ServerPlayer player,
                                             IQuestCapability cap,
                                             QuestRuntimeData data,
                                             QuestDefinition def,
                                             String phaseId) {
        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase == null || !data.isPhaseActive(phaseId)) return;

        List<ObjectiveEntry> objectives = phase.getObjectives();
        for (int i = 0; i < objectives.size(); i++) {
            if (data.getObjectiveProgress(phaseId, i) < objectives.get(i).getRequiredCount()) {
                return;
            }
        }

        MinecraftForge.EVENT_BUS.post(new QuestPhaseCompletedEvent(
                player, ResourceLocation.parse(data.getQuestId()), phase.getPhaseId()));

        grantRewards(player, phase.getPhaseRewards(), "phase");
        unregisterPhaseObjectives(player, def, phase);
        data.completePhase(phaseId);

        // choices：该 phase 完成后等待玩家选路，不自动推进
        if (phase.hasChoices()) {
            syncQuestStateAndPush(player, data);
            return;
        }

        // 自动解锁后继（可多条）
        Set<ResourceLocation> completedQuests = cap.getCompletedQuestLocations();
        for (PhaseTransition tr : phase.getTransitions()) {
            if (tr.requiresChoice()) continue;
            boolean ok = tr.getCondition() == null ||
                    tr.getCondition().test(player, completedQuests, cap.getAllFlags(), cap.getAllVariables());
            if (!ok) continue;

            PhaseDefinition next = def.getPhase(tr.getTargetPhaseId());
            if (next == null) continue;

            data.activatePhase(next.getPhaseId(), next.getObjectives().size());
            registerPhaseObjectives(player, def, next);
        }

        // 没有任何活跃 phase 才算 quest 完成
        if (shouldCompleteQuest(def, data)) {
            completeQuest(player, cap, data, def);
        } else {
            syncQuestStateAndPush(player, data);
        }
    }

    // ═══════════════════════════════════════════════════════
    // 阶段推进（显式推进，用于 choice 等）
    // ═══════════════════════════════════════════════════════

    public static void advanceToPhase(ServerPlayer player,
                                      IQuestCapability cap,
                                      QuestRuntimeData data,
                                      QuestDefinition def,
                                      String nextPhaseId) {
        PhaseDefinition nextPhase = def.getPhase(nextPhaseId);
        if (nextPhase == null) {
            LOGGER.error("[ArcQuest] Target phase not found: {}/{}", def.getId(), nextPhaseId);
            return;
        }

        String fromPhaseId = data.getCurrentPhaseId();

        data.activatePhase(nextPhaseId, nextPhase.getObjectives().size());
        registerPhaseObjectives(player, def, nextPhase);

        syncQuestStateAndPush(player, data);
        QuestEventBus.fire(QuestChangeEvent.phaseChanged(def.getId(), fromPhaseId, nextPhaseId));
        MinecraftForge.EVENT_BUS.post(new QuestPhaseChangedEvent(player, def.getId(), fromPhaseId, nextPhaseId));
    }

    // ═══════════════════════════════════════════════════════
    // 分支选择
    // ═══════════════════════════════════════════════════════

    public static boolean handlePlayerChoice(ServerPlayer player,
                                             String questId,
                                             String phaseId,
                                             int choiceIndex) {
        return handlePlayerChoiceWithCode(player, questId, phaseId, choiceIndex) == QuestRejectCodeDictionary.Code.OK;
    }

    // 兼容旧入口
    public static boolean handlePlayerChoice(ServerPlayer player,
                                             String questId,
                                             int choiceIndex) {
        return handlePlayerChoiceWithCode(player, questId, "", choiceIndex) == QuestRejectCodeDictionary.Code.OK;
    }

    public static QuestRejectCodeDictionary.Code handlePlayerChoiceWithCode(ServerPlayer player,
                                                                            String questId,
                                                                            String phaseId,
                                                                            int choiceIndex) {
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);

        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data == null) return QuestRejectCodeDictionary.Code.NOT_ACTIVE;

        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) return QuestRejectCodeDictionary.Code.QUEST_NOT_FOUND;

        String resolvedPhaseId = phaseId;
        if (resolvedPhaseId == null || resolvedPhaseId.isEmpty()) {
            for (String pid : data.getActivePhaseIds()) {
                PhaseDefinition p = def.getPhase(pid);
                if (p != null && p.hasChoices()) {
                    resolvedPhaseId = pid;
                    break;
                }
            }
        }

        if (resolvedPhaseId == null || resolvedPhaseId.isEmpty()) {
            return QuestRejectCodeDictionary.Code.PHASE_NOT_FOUND;
        }
        if (!data.isPhaseActive(resolvedPhaseId)) {
            return QuestRejectCodeDictionary.Code.PHASE_NOT_FOUND;
        }

        PhaseDefinition currentPhase = def.getPhase(resolvedPhaseId);
        if (currentPhase == null) return QuestRejectCodeDictionary.Code.PHASE_NOT_FOUND;

        List<ChoiceOption> choices = currentPhase.getChoices();
        if (choiceIndex < 0 || choiceIndex >= choices.size()) {
            LOGGER.warn("[ArcQuest] Invalid choice index {} for quest {} phase {}", choiceIndex, questId, resolvedPhaseId);
            return QuestRejectCodeDictionary.Code.INVALID_CHOICE_INDEX;
        }

        ChoiceOption chosen = choices.get(choiceIndex);

        Set<ResourceLocation> completedQuests = cap.getCompletedQuestLocations();
        ICondition visibleCondition = chosen.getVisibleCondition();
        boolean conditionsMet = visibleCondition == null ||
                visibleCondition.test(player, completedQuests, cap.getAllFlags(), cap.getAllVariables());
        if (!conditionsMet) {
            LOGGER.debug("[ArcQuest] Choice conditions not met for index {}", choiceIndex);
            return QuestRejectCodeDictionary.Code.CHOICE_CONDITION_NOT_MET;
        }

        String flagToSet = chosen.getFlagToSet();
        if (flagToSet != null && !flagToSet.isEmpty()) {
            cap.setFlag(flagToSet);
            LOGGER.debug("[ArcQuest] Set flag '{}' from choice", flagToSet);
        }

        String targetPhaseId = chosen.getTargetPhaseId();
        if (targetPhaseId != null && !targetPhaseId.isEmpty()) {
            data.completePhase(resolvedPhaseId);
            unregisterPhaseObjectives(player, def, currentPhase);

            advanceToPhase(player, cap, data, def, targetPhaseId);
            return QuestRejectCodeDictionary.Code.OK;
        }

        LOGGER.warn("[ArcQuest] Choice has no target phase: {}", choiceIndex);
        return QuestRejectCodeDictionary.Code.CHOICE_TARGET_PHASE_MISSING;
    }

    // ═══════════════════════════════════════════════════════
    // 任务完成 / 失败 / 放弃
    // ═══════════════════════════════════════════════════════

    private static void completeQuest(ServerPlayer player,
                                      IQuestCapability cap,
                                      QuestRuntimeData data,
                                      QuestDefinition def) {
        doCompleteQuest(player, cap, data, def, "completed");
    }

    public static void failQuest(ServerPlayer player, String questId) {
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data == null) return;

        data.setState(QuestState.FAILED);
        cap.markFailed(questId);
        ObjectiveTracker.INSTANCE.unregisterQuest(player.getUUID(), questId);

        syncQuestStateAndPush(player, data);
        QuestEventBus.fire(QuestChangeEvent.questFailed(ResourceLocation.parse(questId)));
        MinecraftForge.EVENT_BUS.post(new QuestFailedEvent(player, ResourceLocation.parse(questId)));
    }

    public static boolean abandonQuest(ServerPlayer player, String questId) {
        return abandonQuestWithCode(player, questId) == QuestRejectCodeDictionary.Code.OK;
    }

    public static QuestRejectCodeDictionary.Code abandonQuestWithCode(ServerPlayer player, String questId) {
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        if (!cap.isQuestActive(questId)) return QuestRejectCodeDictionary.Code.NOT_ACTIVE;

        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data != null) {
            data.setState(QuestState.FAILED);
        }
        cap.markFailed(questId);
        cap.removeActiveQuest(questId);
        ObjectiveTracker.INSTANCE.unregisterQuest(player.getUUID(), questId);

        syncFullDataAndPush(player, cap);
        QuestEventBus.fire(QuestChangeEvent.questFailed(ResourceLocation.parse(questId)));
        return QuestRejectCodeDictionary.Code.OK;
    }

    public static void forceComplete(ServerPlayer player, String questId) {
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data == null) return;

        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) return;

        doCompleteQuest(player, cap, data, def, "force-completed");
    }

    private static void doCompleteQuest(ServerPlayer player,
                                        IQuestCapability cap,
                                        QuestRuntimeData data,
                                        QuestDefinition def,
                                        String logPrefix) {
        String questId = data.getQuestId();

        grantRewards(player, def.getCompletionRewards(), "completion");
        def.getFlagsToSetOnComplete().forEach(cap::setFlag);

        data.setState(QuestState.COMPLETED);
        cap.markCompleted(questId);

        ObjectiveTracker.INSTANCE.unregisterQuest(player.getUUID(), questId);

        LOGGER.info("[ArcQuest] Player {} {} quest: {}",
                player.getGameProfile().getName(), logPrefix, questId);

        syncQuestStateAndPush(player, data);
        syncFlagsVarsAndPush(player, cap);
        QuestEventBus.fire(QuestChangeEvent.questCompleted(ResourceLocation.parse(questId)));
        MinecraftForge.EVENT_BUS.post(new QuestCompletedEvent(player, ResourceLocation.parse(questId)));
    }

    public static void syncToClient(ServerPlayer player, String questId) {
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data != null) {
            syncQuestStateAndPush(player, data);
        }
    }

    private static void syncQuestStateAndPush(ServerPlayer player, QuestRuntimeData data) {
        QuestSyncCoordinator.syncQuestStateAndPush(player, data);
    }

    private static void syncFlagsVarsAndPush(ServerPlayer player, IQuestCapability cap) {
        QuestSyncCoordinator.syncFlagsVarsAndPush(player, cap);
    }

    private static void syncFullDataAndPush(ServerPlayer player, IQuestCapability cap) {
        QuestSyncCoordinator.syncFullDataAndPush(player, cap);
    }

    private static void syncDeltaProgressAndPush(ServerPlayer player,
                                                 String questId,
                                                 String phaseId,
                                                 int objIndex,
                                                 int newProgress) {
        QuestSyncCoordinator.syncDeltaProgressAndPush(player, questId, phaseId, objIndex, newProgress);
    }

    // ═══════════════════════════════════════════════════════
    // 索引管理
    // ═══════════════════════════════════════════════════════

    public static void rebuildTrackingIndex(ServerPlayer player, IQuestCapability cap) {
        ObjectiveTracker.INSTANCE.unregisterPlayer(player.getUUID());

        for (Map.Entry<String, QuestRuntimeData> entry : cap.getAllActiveQuests().entrySet()) {
            QuestRuntimeData data = entry.getValue();
            if (data.getState() != QuestState.ACTIVE) continue;

            QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(data.getQuestId()));
            if (def == null) continue;

            for (String phaseId : data.getActivePhaseIds()) {
                PhaseDefinition phase = def.getPhase(phaseId);
                if (phase == null) continue;
                registerPhaseObjectives(player, def, phase);
            }
        }
    }

    public static void registerPhaseObjectives(ServerPlayer player,
                                               QuestDefinition def,
                                               PhaseDefinition phase) {
        List<ObjectiveEntry> objectives = phase.getObjectives();
        for (int i = 0; i < objectives.size(); i++) {
            ObjectiveEntry obj = objectives.get(i);
            TrackedObjective tracked = new TrackedObjective(
                    player.getUUID(),
                    def.getId(),
                    phase.getPhaseId(),
                    i,
                    obj
            );
            ObjectiveTracker.INSTANCE.register(tracked);
        }
    }

    private static void unregisterPhaseObjectives(ServerPlayer player,
                                                  QuestDefinition def,
                                                  PhaseDefinition phase) {
        List<ObjectiveEntry> objectives = phase.getObjectives();
        for (int i = 0; i < objectives.size(); i++) {
            ObjectiveEntry obj = objectives.get(i);
            TrackedObjective tracked = new TrackedObjective(
                    player.getUUID(),
                    def.getId(),
                    phase.getPhaseId(),
                    i,
                    obj
            );
            ObjectiveTracker.INSTANCE.unregister(tracked);
        }
    }

    private static void grantRewards(ServerPlayer player, List<IReward> rewards, String context) {
        for (IReward reward : rewards) {
            try {
                reward.grant(player);
            } catch (Exception e) {
                LOGGER.error("[ArcQuest] Error granting {} reward: {}", context, e.getMessage(), e);
            }
        }
    }
}