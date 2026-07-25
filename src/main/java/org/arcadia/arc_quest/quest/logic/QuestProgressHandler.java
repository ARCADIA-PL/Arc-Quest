package org.arcadia.arc_quest.quest.logic;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;
import org.arcadia.arc_quest.api.event.quest.*;
import org.arcadia.arc_quest.core.CoreProcessors;
import org.arcadia.arc_quest.core.execution.CoreRule;
import org.arcadia.arc_quest.core.execution.ExecutionObserver;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.event.QuestChangeEvent;
import org.arcadia.arc_quest.quest.event.QuestEventBus;
import org.arcadia.arc_quest.quest.logic.profile.CollectionEntryUpdateResult;
import org.arcadia.arc_quest.quest.logic.profile.CollectionQuestEngine;
import org.arcadia.arc_quest.quest.logic.profile.CollectionVisibilityUpdateResult;
import org.arcadia.arc_quest.quest.network.QuestRejectCodeDictionary;
import org.arcadia.arc_quest.quest.network.QuestSyncCoordinator;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.tracking.ObjectiveKey;
import org.arcadia.arc_quest.quest.tracking.ObjectiveTracker;
import org.arcadia.arc_quest.quest.tracking.TrackedObjective;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 任务进度推进核心逻辑（服务端）。
 * 并行模型：同一 Quest 内可有多个 active phase 同时推进。
 */
public final class QuestProgressHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private QuestProgressHandler() {
    }

    public static boolean acceptQuest(ServerPlayer player, String questId) {
        return acceptQuestWithCode(player, questId) == QuestRejectCodeDictionary.Code.OK;
    }

    // ═══════════════════════════════════════════════════════
    //  接受任务
    // ═══════════════════════════════════════════════════════
    private static boolean shouldCompleteQuest(QuestDefinition def, QuestRuntimeData qdata) {
        int done = qdata.getCompletedPhaseIds().size();
        return switch (def.getCompletionPolicy()) {
            case ALL -> done >= def.getPhaseIds().size();
            case ANY -> done >= 1;
            case N_OF_M -> done >= Math.max(1, def.getCompletionRequiredCount());
            case SPECIFIC_PHASE -> {
                String target = def.getCompletionTargetPhaseId();
                yield target != null && qdata.getCompletedPhaseIds().contains(target);
            }
        };
    }

    public static QuestRejectCodeDictionary.Code acceptQuestWithCode(ServerPlayer player, String questId) {
        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) {
            LOGGER.warn("[ArcQuest] Cannot accept unknown quest: {}", questId);
            return QuestRejectCodeDictionary.Code.QUEST_NOT_FOUND;
        }

        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);

        if (def.isCollectionQuest()) {
            return CollectionQuestEngine.acceptQuest(player, data, def);
        }

        Set<ResourceLocation> completedQuests = data.getCompletedQuestLocations();
        PhaseDefinition firstPhase = def.getInitialPhase();
        QuestAcceptanceContext acceptance = new QuestAcceptanceContext(
                player, data, def, questId, firstPhase,
                new QuestConditionContext(player, completedQuests,
                        data.getAllFlags(), data.getAllVariables()));
        List<CoreRule<QuestAcceptanceContext, QuestRejectCodeDictionary.Code>> rules = List.of(
                CoreRule.require(context -> !context.data().isQuestActive(context.questId()),
                        QuestRejectCodeDictionary.Code.ALREADY_ACTIVE),
                CoreRule.require(context -> (!context.data().isQuestCompleted(context.questId())
                                && !context.data().isQuestFailed(context.questId()))
                                || context.definition().isRepeatable(),
                        QuestRejectCodeDictionary.Code.ALREADY_COMPLETED_NOT_REPEATABLE),
                CoreRule.require(context -> CoreProcessors.get().conditions().all(
                                context.definition().getUnlockConditions(), context.conditionContext()),
                        QuestRejectCodeDictionary.Code.UNLOCK_CONDITION_NOT_MET),
                CoreRule.require(context -> context.initialPhase() != null,
                        QuestRejectCodeDictionary.Code.NO_INITIAL_PHASE)
        );
        var execution = CoreProcessors.get().executions().execute(
                acceptance, rules, QuestProgressHandler::applyQuestAcceptance,
                new ExecutionObserver<QuestAcceptanceContext, QuestRejectCodeDictionary.Code,
                        QuestRejectCodeDictionary.Code>() {
                    @Override
                    public void onSucceeded(QuestAcceptanceContext context,
                                            QuestRejectCodeDictionary.Code value) {
                        publishQuestAccepted(context);
                    }
                });
        return execution.succeeded() ? execution.value() : execution.failure();
    }

    public static void incrementObjective(ServerPlayer player,
                                          String questId,
                                          String phaseId,
                                          int objIndex,
                                          int amount) {
        if (amount <= 0) return;

        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;
        QuestRuntimeData qdata = data.getActiveQuest(questId);
        if (qdata == null || qdata.getState() != QuestState.ACTIVE) return;

        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) return;
        if (def.isCollectionQuest()) return;
        if (!qdata.isPhaseActive(phaseId)) return;

        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase == null) return;
        if (objIndex < 0 || objIndex >= phase.getObjectives().size()) return;

        ObjectiveEntry objEntry = phase.getObjectives().get(objIndex);
        int required = resolveRequiredCount(player, objEntry, data);

        int currentProgress = qdata.getObjectiveProgress(phaseId, objIndex);
        if (currentProgress >= required) return;

        int newProgress = qdata.incrementProgress(phaseId, objIndex, amount, required);

        syncDeltaProgressAndPush(player, questId, phaseId, objIndex, newProgress);

        QuestEventBus.fire(QuestChangeEvent.objectiveProgressed(
                ResourceLocation.parse(questId), objIndex, newProgress, required));

        MinecraftForge.EVENT_BUS.post(new QuestProgressChangedEvent(
                player, ResourceLocation.parse(questId), phaseId,
                objIndex, currentProgress, newProgress, required));

        checkPhaseCompletion(player, data, qdata, def, phaseId);
    }

    public static void incrementCollectionEntry(ServerPlayer player,
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

        CollectionEntryUpdateResult result = CollectionQuestEngine.incrementEntryWithResult(player, data, def, qdata, phaseId, amount);
        if (result.isChanged()) {
            syncQuestStateAndPush(player, qdata);
        }
    }

    public static void revealCollectionEntry(ServerPlayer player,
                                             String questId,
                                             String phaseId) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;
        QuestRuntimeData qdata = data.getActiveQuest(questId);
        if (qdata == null || qdata.getState() != QuestState.ACTIVE || !qdata.hasCollectionData()) return;

        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null || !def.isCollectionQuest()) return;

        CollectionVisibilityUpdateResult result = CollectionQuestEngine.revealEntry(def, qdata, phaseId);
        if (result.isChanged()) {
            syncQuestStateAndPush(player, qdata);
        }
    }

    public static void refreshCollectionVisibility(ServerPlayer player, String questId) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;
        QuestRuntimeData qdata = data.getActiveQuest(questId);
        if (qdata == null || qdata.getState() != QuestState.ACTIVE || !qdata.hasCollectionData()) return;

        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null || !def.isCollectionQuest()) return;

        if (CollectionQuestEngine.refreshVisibility(player, data, def, qdata) > 0) {
            syncQuestStateAndPush(player, qdata);
        }
    }

    public static void discoverCollectionEntry(ServerPlayer player,
                                               String questId,
                                               String phaseId) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;
        QuestRuntimeData qdata = data.getActiveQuest(questId);
        if (qdata == null || qdata.getState() != QuestState.ACTIVE || !qdata.hasCollectionData()) return;

        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null || !def.isCollectionQuest()) return;

        CollectionEntryUpdateResult result = CollectionQuestEngine.discoverEntryWithResult(def, qdata, phaseId);
        if (result.isChanged()) {
            syncQuestStateAndPush(player, qdata);
        }
    }

    public static void addCollectionUniqueKey(ServerPlayer player,
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

        CollectionEntryUpdateResult result = CollectionQuestEngine.addUniqueProgressWithResult(player, data, def, qdata, phaseId, uniqueKey);
        if (result.isChanged()) {
            syncQuestStateAndPush(player, qdata);
        }
    }

    // ═══════════════════════════════════════════════════════
    //  目标推进（并行 phase 维度）
    // ═══════════════════════════════════════════════════════
    private static void checkPhaseCompletion(ServerPlayer player,
                                             ArcQuestPlayer data,
                                             QuestRuntimeData qdata,
                                             QuestDefinition def,
                                             String phaseId) {
        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase == null || !qdata.isPhaseActive(phaseId)) return;

        List<ObjectiveEntry> objectives = phase.getObjectives();
        for (int i = 0; i < objectives.size(); i++) {
            ObjectiveEntry objective = objectives.get(i);
            if (objective.getType().equals(ObjectiveType.NULL)) {
                continue;
            }
            if (qdata.getObjectiveProgress(phaseId, i) < resolveRequiredCount(player, objective, data)) {
                return;
            }
        }

        MinecraftForge.EVENT_BUS.post(new QuestPhaseCompletedEvent(
                player, ResourceLocation.parse(qdata.getQuestId()), phase.getPhaseId()));

        grantRewards(player, phase.getPhaseRewards(), "phase");
        unregisterPhaseObjectives(player, def, phase);

        ActivationContext ctx = new ActivationContext();
        for (String flag : phase.getFlagsToSetOnComplete()) {
            data.setFlag(flag);
            ctx.flagsChanged = true;
        }

        if (!phase.shouldAutoAdvanceOnComplete()) {
            qdata.markPhasePendingManualAdvance(phaseId);
            QuestMarkerService.refreshQuestMarkers(player, data, qdata, def);
            syncQuestStateAndPush(player, qdata);
            if (ctx.flagsChanged) {
                syncFlagsVarsAndPush(player, data);
            }
            return;
        }

        qdata.completePhase(phaseId);

        // choices：该 phase 完成后等待玩家选路，不自动推进 transition
        if (phase.hasChoices()) {
            // 但允许 auto enter phase 扫描（如配置了 autoEnterByCondition=true）
            tryAutoEnterPhases(player, data, qdata, def, phaseId, ctx);

            if (shouldCompleteQuest(def, qdata)) {
                completeQuest(player, data, qdata, def);
                return;
            }

            QuestMarkerService.refreshQuestMarkers(player, data, qdata, def);
            syncQuestStateAndPush(player, qdata);
            if (ctx.flagsChanged) {
                syncFlagsVarsAndPush(player, data);
            }
            return;
        }

        Set<ResourceLocation> completedQuests = data.getCompletedQuestLocations();

        // 自动解锁后继（可多条，支持 thenGoToIf）
        for (PhaseTransition tr : phase.getTransitions()) {
            boolean ok = tr.getCondition() == null
                    || evaluateCondition(tr.getCondition(), player, completedQuests, data);
            if (!ok) continue;

            activatePhase(player, data, qdata, def, phaseId, tr.getTargetPhaseId(), true, ctx);
        }

        // enterCondition 自动扫描（仅 autoEnterByCondition=true 的 phase）
        tryAutoEnterPhases(player, data, qdata, def, phaseId, ctx);
        processImmediatelySatisfiedPhases(player, data, qdata, def);

        if (shouldCompleteQuest(def, qdata)) {
            completeQuest(player, data, qdata, def);
        } else {
            syncQuestStateAndPush(player, qdata);
            if (ctx.flagsChanged) {
                syncFlagsVarsAndPush(player, data);
            }
        }
    }

    public static void advanceToPhase(ServerPlayer player,
                                      ArcQuestPlayer data,
                                      QuestRuntimeData qdata,
                                      QuestDefinition def,
                                      String nextPhaseId) {
        String fromPhaseId = qdata.getCurrentPhaseId();

        ActivationContext ctx = new ActivationContext();
        boolean ok = activatePhase(player, data, qdata, def, fromPhaseId, nextPhaseId, true, ctx);
        if (!ok) {
            LOGGER.warn("[ArcQuest] Target phase cannot be activated: {}/{}", def.getId(), nextPhaseId);
            return;
        }

        tryAutoEnterPhases(player, data, qdata, def, fromPhaseId, ctx);
        processImmediatelySatisfiedPhases(player, data, qdata, def);

        QuestMarkerService.refreshQuestMarkers(player, data, qdata, def);
        syncQuestStateAndPush(player, qdata);
        if (ctx.flagsChanged) {
            syncFlagsVarsAndPush(player, data);
        }
    }

    // ═══════════════════════════════════════════════════════
    // 阶段推进（显式推进，用于 choice 等）
    // ═══════════════════════════════════════════════════════
    public static QuestRejectCodeDictionary.Code confirmManualPhaseAdvance(ServerPlayer player,
                                                                           String questId,
                                                                           String phaseId) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return QuestRejectCodeDictionary.Code.NOT_ACTIVE;
        QuestRuntimeData qdata = data.getActiveQuest(questId);
        if (phaseId == null || phaseId.isEmpty() || !qdata.isPhasePendingManualAdvance(phaseId)) {
            return QuestRejectCodeDictionary.Code.PHASE_NOT_FOUND;
        }
        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) return QuestRejectCodeDictionary.Code.QUEST_NOT_FOUND;
        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase == null) return QuestRejectCodeDictionary.Code.PHASE_NOT_FOUND;
        qdata.clearPhasePendingManualAdvance(phaseId);
        qdata.completePhase(phaseId);
        ActivationContext ctx = new ActivationContext();
        for (String flag : phase.getFlagsToSetOnComplete()) {
            data.setFlag(flag);
            ctx.flagsChanged = true;
        }
        if (phase.hasChoices()) {
            tryAutoEnterPhases(player, data, qdata, def, phaseId, ctx);
            QuestMarkerService.refreshQuestMarkers(player, data, qdata, def);
            syncQuestStateAndPush(player, qdata);
            if (ctx.flagsChanged) syncFlagsVarsAndPush(player, data);
            return QuestRejectCodeDictionary.Code.OK;
        }
        Set<ResourceLocation> completedQuests = data.getCompletedQuestLocations();
        for (PhaseTransition tr : phase.getTransitions()) {
            boolean ok = tr.getCondition() == null
                    || evaluateCondition(tr.getCondition(), player, completedQuests, data);
            if (ok) activatePhase(player, data, qdata, def, phaseId, tr.getTargetPhaseId(), true, ctx);
        }
        tryAutoEnterPhases(player, data, qdata, def, phaseId, ctx);
        processImmediatelySatisfiedPhases(player, data, qdata, def);
        if (shouldCompleteQuest(def, qdata)) {
            completeQuest(player, data, qdata, def);
        } else {
            QuestMarkerService.refreshQuestMarkers(player, data, qdata, def);
            syncQuestStateAndPush(player, qdata);
        }
        if (ctx.flagsChanged) syncFlagsVarsAndPush(player, data);
        return QuestRejectCodeDictionary.Code.OK;
    }

    public static boolean handlePlayerChoice(ServerPlayer player,
                                             String questId,
                                             String phaseId,
                                             int choiceIndex) {
        return handlePlayerChoiceWithCode(player, questId, phaseId, choiceIndex) == QuestRejectCodeDictionary.Code.OK;
    }

    // ═══════════════════════════════════════════════════════
    // 分支选择
    // ═══════════════════════════════════════════════════════

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
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);

        if (data == null) return QuestRejectCodeDictionary.Code.NOT_ACTIVE;
        QuestRuntimeData qdata = data.getActiveQuest(questId);

        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) return QuestRejectCodeDictionary.Code.QUEST_NOT_FOUND;

        String resolvedPhaseId = phaseId;
        if (resolvedPhaseId == null || resolvedPhaseId.isEmpty()) {
            for (String pid : qdata.getActivePhaseIds()) {
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
        if (!qdata.isPhaseActive(resolvedPhaseId)) {
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

        Set<ResourceLocation> completedQuests = data.getCompletedQuestLocations();
        ICondition visibleCondition = chosen.getVisibleCondition();
        boolean conditionsMet = visibleCondition == null
                || evaluateCondition(visibleCondition, player, completedQuests, data);
        if (!conditionsMet) {
            LOGGER.debug("[ArcQuest] Choice conditions not met for index {}", choiceIndex);
            return QuestRejectCodeDictionary.Code.CHOICE_CONDITION_NOT_MET;
        }

        ActivationContext ctx = new ActivationContext();

        String flagToSet = chosen.getFlagToSet();
        if (flagToSet != null && !flagToSet.isEmpty()) {
            data.setFlag(flagToSet);
            ctx.flagsChanged = true;
            LOGGER.debug("[ArcQuest] Set flag '{}' from choice", flagToSet);
        }

        String targetPhaseId = chosen.getTargetPhaseId();
        if (targetPhaseId == null || targetPhaseId.isEmpty()) {
            LOGGER.warn("[ArcQuest] Choice has no target phase: {}", choiceIndex);
            return QuestRejectCodeDictionary.Code.CHOICE_TARGET_PHASE_MISSING;
        }

        // 完成当前 choice phase
        qdata.completePhase(resolvedPhaseId);
        unregisterPhaseObjectives(player, def, currentPhase);
        for (String flag : currentPhase.getFlagsToSetOnComplete()) {
            data.setFlag(flag);
            ctx.flagsChanged = true;
        }

        // 并行增强：激活"选择目标 + 当前 phase 里其它满足条件的 transition"
        Set<String> toActivate = new LinkedHashSet<>();
        toActivate.add(targetPhaseId);

        for (PhaseTransition tr : currentPhase.getTransitions()) {
            String pid = tr.getTargetPhaseId();
            if (pid == null || pid.isEmpty() || pid.equals(targetPhaseId)) continue;

            ICondition cond = tr.getCondition();
            boolean ok = cond == null || evaluateCondition(cond, player, completedQuests, data);
            if (!ok) continue;

            toActivate.add(pid);
        }

        for (String pid : toActivate) {
            activatePhase(player, data, qdata, def, resolvedPhaseId, pid, true, ctx);
        }

        MinecraftForge.EVENT_BUS.post(new QuestChoiceResolvedEvent(
                player,
                ResourceLocation.parse(questId),
                resolvedPhaseId,
                choiceIndex,
                chosen.getDisplayText().getString(),
                targetPhaseId
        ));

        // enterCondition 自动扫描（仅 autoEnterByCondition=true 的 phase）
        tryAutoEnterPhases(player, data, qdata, def, resolvedPhaseId, ctx);
        processImmediatelySatisfiedPhases(player, data, qdata, def);

        if (shouldCompleteQuest(def, qdata)) {
            completeQuest(player, data, qdata, def);
        } else {
            QuestMarkerService.refreshQuestMarkers(player, data, qdata, def);
            syncQuestStateAndPush(player, qdata);
            if (ctx.flagsChanged) {
                syncFlagsVarsAndPush(player, data);
            }
        }

        if (ctx.activatedCount == 0 && !shouldCompleteQuest(def, qdata)) {
            LOGGER.warn("[ArcQuest] Choice resolved but no next phase activated: quest={}, phase={}, choice={}",
                    questId, resolvedPhaseId, choiceIndex);
            return QuestRejectCodeDictionary.Code.CHOICE_TARGET_PHASE_MISSING;
        }

        return QuestRejectCodeDictionary.Code.OK;
    }

    private static void completeQuest(ServerPlayer player,
                                      ArcQuestPlayer data,
                                      QuestRuntimeData qdata,
                                      QuestDefinition def) {
        doCompleteQuest(player, data, qdata, def, "completed");
    }

    // ═══════════════════════════════════════════════════════
    // 任务完成 / 失败 / 放弃
    // ═══════════════════════════════════════════════════════
    public static void failQuest(ServerPlayer player, String questId) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;
        QuestRuntimeData qdata = data.getActiveQuest(questId);

        qdata.setState(QuestState.FAILED);
        data.markFailed(questId);
        ObjectiveTracker.INSTANCE.unregisterQuest(player.getUUID(), questId);
        QuestMarkerService.clearQuestMarkers(data, questId);

        syncQuestStateAndPush(player, qdata);
        QuestEventBus.fire(QuestChangeEvent.questFailed(ResourceLocation.parse(questId)));
        MinecraftForge.EVENT_BUS.post(new QuestFailedEvent(player, ResourceLocation.parse(questId)));
        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def != null) playChapterSound(player, def.getChapterFailSound());
    }

    public static boolean abandonQuest(ServerPlayer player, String questId) {
        return abandonQuestWithCode(player, questId) == QuestRejectCodeDictionary.Code.OK;
    }

    public static QuestRejectCodeDictionary.Code abandonQuestWithCode(ServerPlayer player, String questId) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (!data.isQuestActive(questId)) {
            return QuestRejectCodeDictionary.Code.NOT_ACTIVE;
        }

        QuestRuntimeData qdata = data.getActiveQuest(questId);
        if (data != null) {
            qdata.setState(QuestState.FAILED);
        }

        data.markFailed(questId);
        ObjectiveTracker.INSTANCE.unregisterQuest(player.getUUID(), questId);
        QuestMarkerService.clearQuestMarkers(data, questId);

        syncFullDataAndPush(player, data);
        QuestEventBus.fire(QuestChangeEvent.questFailed(ResourceLocation.parse(questId)));
        MinecraftForge.EVENT_BUS.post(new QuestFailedEvent(player, ResourceLocation.parse(questId)));
        MinecraftForge.EVENT_BUS.post(new QuestAbandonedEvent(player, ResourceLocation.parse(questId)));
        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def != null) playChapterSound(player, def.getChapterFailSound());
        return QuestRejectCodeDictionary.Code.OK;
    }

    public static void forceComplete(ServerPlayer player, String questId) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;
        QuestRuntimeData qdata = data.getActiveQuest(questId);

        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) return;

        doCompleteQuest(player, data, qdata, def, "force-completed");
    }

    public static void forceCompletePhase(ServerPlayer player, String questId, String phaseId) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;
        QuestRuntimeData qdata = data.getActiveQuest(questId);
        if (qdata == null) return;

        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) return;

        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase == null) return;

        if (!qdata.isPhaseActive(phaseId)) {
            qdata.activatePhase(phaseId, phase.getObjectives().size());
            registerPhaseObjectives(player, def, phase);
        }

        for (int i = 0; i < phase.getObjectives().size(); i++) {
            qdata.setObjectiveProgress(phaseId, i, phase.getObjectives().get(i).getRequiredCount());
        }

        qdata.invalidatePhaseCache();
        checkPhaseCompletion(player, data, qdata, def, phaseId);
    }

    private static void doCompleteQuest(ServerPlayer player,
                                        ArcQuestPlayer data,
                                        QuestRuntimeData qdata,
                                        QuestDefinition def,
                                        String logPrefix) {
        String questId = qdata.getQuestId();

        grantRewards(player, def.getCompletionRewards(), "completion");
        def.getFlagsToSetOnComplete().forEach(data::setFlag);

        qdata.setState(QuestState.COMPLETED);
        data.markCompleted(questId);

        ObjectiveTracker.INSTANCE.unregisterQuest(player.getUUID(), questId);
        QuestMarkerService.clearQuestMarkers(data, questId);

        LOGGER.info("[ArcQuest] Player {} {} quest: {}",
                player.getGameProfile().getName(), logPrefix, questId);

        syncQuestStateAndPush(player, qdata);
        syncFlagsVarsAndPush(player, data);
        QuestEventBus.fire(QuestChangeEvent.questCompleted(ResourceLocation.parse(questId)));
        MinecraftForge.EVENT_BUS.post(new QuestCompletedEvent(player, ResourceLocation.parse(questId)));
        playChapterSound(player, def.getChapterCompleteSound());
    }

    public static void syncToClient(ServerPlayer player, String questId) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        QuestRuntimeData qdata = data.getActiveQuest(questId);
        if (data != null) {
            syncQuestStateAndPush(player, qdata);
        }
    }

    private static void playChapterSound(ServerPlayer player, SoundEvent sound) {
        if (player == null || sound == null) return;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void syncQuestStateAndPush(ServerPlayer player, QuestRuntimeData data) {
        QuestSyncCoordinator.syncQuestStateAndPush(player, data);
    }

    private static void syncFlagsVarsAndPush(ServerPlayer player, ArcQuestPlayer data) {
        QuestSyncCoordinator.syncFlagsVarsAndPush(player, data);
    }

    private static void syncFullDataAndPush(ServerPlayer player, ArcQuestPlayer data) {
        QuestSyncCoordinator.syncFullDataAndPush(player, data);
    }

    private static void syncDeltaProgressAndPush(ServerPlayer player,
                                                 String questId,
                                                 String phaseId,
                                                 int objIndex,
                                                 int newProgress) {
        QuestSyncCoordinator.syncDeltaProgressAndPush(player, questId, phaseId, objIndex, newProgress);
    }

    public static void rebuildTrackingIndex(ServerPlayer player, ArcQuestPlayer data) {
        ObjectiveTracker.INSTANCE.unregisterPlayer(player.getUUID());

        int activeQuestCount = 0;
        for (Map.Entry<String, QuestRuntimeData> entry : data.getAllActiveQuests().entrySet()) {
            QuestRuntimeData qdata = entry.getValue();
            if (qdata.getState() != QuestState.ACTIVE) continue;
            activeQuestCount++;

            QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(qdata.getQuestId()));
            if (def == null) continue;

            for (String phaseId : qdata.getActivePhaseIds()) {
                PhaseDefinition phase = def.getPhase(phaseId);
                if (phase == null) continue;
                registerPhaseObjectives(player, def, phase);
            }

            QuestMarkerService.refreshQuestMarkers(player, data, qdata, def);
        }

        MinecraftForge.EVENT_BUS.post(new QuestTrackerRebuiltEvent(player, activeQuestCount));
    }

    // ═══════════════════════════════════════════════════════
    // 索引管理
    // ═══════════════════════════════════════════════════════
    public static void registerPhaseObjectives(ServerPlayer player,
                                               QuestDefinition def,
                                               PhaseDefinition phase) {
        List<ObjectiveEntry> objectives = phase.getObjectives();
        for (int i = 0; i < objectives.size(); i++) {
            ObjectiveEntry obj = objectives.get(i);
            List<ResourceLocation> keys = objectiveKeyTargets(obj);
            for (ResourceLocation keyTarget : keys) {
                TrackedObjective tracked = new TrackedObjective(
                        player.getUUID(),
                        def.getId(),
                        phase.getPhaseId(),
                        i,
                        new ObjectiveKey(obj.getType(), keyTarget),
                        obj.getRequiredCount()
                );
                ObjectiveTracker.INSTANCE.register(tracked);
            }
        }
    }

    private static void unregisterPhaseObjectives(ServerPlayer player,
                                                  QuestDefinition def,
                                                  PhaseDefinition phase) {
        List<ObjectiveEntry> objectives = phase.getObjectives();
        for (int i = 0; i < objectives.size(); i++) {
            ObjectiveEntry obj = objectives.get(i);
            List<ResourceLocation> keys = objectiveKeyTargets(obj);
            for (ResourceLocation keyTarget : keys) {
                TrackedObjective tracked = new TrackedObjective(
                        player.getUUID(),
                        def.getId(),
                        phase.getPhaseId(),
                        i,
                        new ObjectiveKey(obj.getType(), keyTarget),
                        obj.getRequiredCount()
                );
                ObjectiveTracker.INSTANCE.unregister(tracked);
            }
        }
    }

    public static int resolveRequiredCount(ServerPlayer player, ObjectiveEntry obj, ArcQuestPlayer data) {
        int fromModifier = obj.resolveRequiredCount(player);

        String modeRaw = obj.getExtra("count_mode");
        String mode = modeRaw == null ? "" : modeRaw.trim().toLowerCase(Locale.ROOT);
        if (mode.isEmpty()) return Math.max(1, fromModifier);

        int base = obj.getExtraInt("count_base", fromModifier);
        int min = obj.getExtraInt("count_min", 1);
        int max = obj.getExtraInt("count_max", -1);

        int variableValue = 0;
        if ("variable".equals(mode)) {
            String var = obj.getExtra("count_var");
            variableValue = (var == null || var.isEmpty()) ? 0 : data.getVariable(var);
        }

        return computeRequiredCount(modeRaw, mode, fromModifier, base, min, max, player.experienceLevel, obj.getExtraInt("count_per_level", 0), variableValue, obj.getExtraInt("count_per_var", 0), obj.getTargetId().toString());
    }

    static int computeRequiredCount(String modeRaw,
                                    String normalizedMode,
                                    int fallbackRequired,
                                    int base,
                                    int min,
                                    int max,
                                    int playerLevel,
                                    int countPerLevel,
                                    int variableValue,
                                    int countPerVar,
                                    String objectiveDebugId) {
        int safeMin = Math.max(1, min);
        int safeMax = max;
        if (safeMax > 0 && safeMax < safeMin) safeMax = safeMin;

        int computed;
        switch (normalizedMode) {
            case "player_level", "level_scale" -> computed = base + Math.max(0, playerLevel) * countPerLevel;
            case "variable" -> computed = base + variableValue * countPerVar;
            case "fixed" -> computed = base;
            default -> {
                LOGGER.warn("[ArcQuest] Unknown count_mode '{}' for objective {}, fallback to requiredCount", modeRaw, objectiveDebugId);
                computed = fallbackRequired;
            }
        }

        computed = Math.max(safeMin, computed);
        if (safeMax > 0) computed = Math.min(safeMax, computed);
        return Math.max(1, computed);
    }

    private static void processImmediatelySatisfiedPhases(ServerPlayer player,
                                                          ArcQuestPlayer data,
                                                          QuestRuntimeData qdata,
                                                          QuestDefinition def) {
        boolean changed;
        do {
            changed = false;
            for (String phaseId : List.copyOf(qdata.getActivePhaseIds())) {
                PhaseDefinition phase = def.getPhase(phaseId);
                if (phase == null) continue;

                if (qdata.isPhaseCompletionCached(phaseId)) {
                    if (!qdata.isPhaseCompletionSatisfied(phaseId)) continue;
                }

                boolean allSatisfied = true;
                for (int i = 0; i < phase.getObjectives().size(); i++) {
                    ObjectiveEntry objective = phase.getObjectives().get(i);
                    if (objective.getType() == ObjectiveType.NULL) continue;
                    if (qdata.getObjectiveProgress(phaseId, i) < resolveRequiredCount(player, objective, data)) {
                        allSatisfied = false;
                        break;
                    }
                }
                if (!allSatisfied) {
                    qdata.setPhaseCompletionCached(phaseId, false);
                    continue;
                }
                int beforeCompleted = qdata.getCompletedPhaseIds().size();
                int beforePending = qdata.getPendingManualAdvancePhaseIds().size();
                checkPhaseCompletion(player, data, qdata, def, phaseId);
                if (qdata.getCompletedPhaseIds().size() != beforeCompleted || qdata.getPendingManualAdvancePhaseIds().size() != beforePending) {
                    changed = true;
                }
            }
        } while (changed);
    }

    public static List<ResourceLocation> objectiveKeyTargets(ObjectiveEntry obj) {
        String tag = obj.getExtra("target_tag");
        if (tag == null || tag.isEmpty()) {
            return List.of(obj.getTargetId());
        }

        ResourceLocation tagId = ResourceLocation.parse(tag);
        TagKey<Item> key = TagKey.create(Registries.ITEM, tagId);
        var named = ForgeRegistries.ITEMS.tags();
        if (named == null) return List.of(obj.getTargetId());

        List<ResourceLocation> ids = new ArrayList<>();
        for (Item taggedItem : named.getTag(key)) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(taggedItem);
            if (id != null) ids.add(id);
        }
        if (ids.isEmpty()) ids.add(obj.getTargetId());
        return ids;
    }

    private static void grantRewards(ServerPlayer player, List<IReward> rewards, String context) {
        for (IReward reward : rewards) {
            try {
                LOGGER.info("[ArcQuest] Granting {} reward to {}: {}", context, player.getGameProfile().getName(), reward.describe());
                reward.grant(player);
            } catch (Exception e) {
                LOGGER.error("[ArcQuest] Error granting {} reward: {}", context, e.getMessage(), e);
            }
        }
    }

    private static boolean canEnterPhase(ServerPlayer player,
                                         ArcQuestPlayer data,
                                         PhaseDefinition phase,
                                         @Nullable QuestRuntimeData qdata) {
        if (data != null && qdata.isEnterConditionCached(phase.getPhaseId())) {
            return qdata.canEnterPhaseCached(phase.getPhaseId());
        }
        ICondition cond = phase.getEnterCondition();
        if (cond == null) return true;
        boolean result = evaluateCondition(cond, player, data.getCompletedQuestLocations(), data);
        if (data != null) qdata.setEnterConditionCached(phase.getPhaseId(), result);
        return result;
    }

    private static QuestRejectCodeDictionary.Code applyQuestAcceptance(QuestAcceptanceContext context) {
        ServerPlayer player = context.player();
        ArcQuestPlayer data = context.data();
        QuestDefinition definition = context.definition();
        PhaseDefinition firstPhase = context.initialPhase();
        String questId = context.questId();
        long acceptedTick = player.getServer() != null ? player.getServer().getTickCount() : 0L;
        var acceptedTime = CoreProcessors.get().time().capture(player);

        QuestRuntimeData runtime = new QuestRuntimeData(
                questId,
                firstPhase.getPhaseId(),
                firstPhase.getObjectives().size(),
                acceptedTick,
                acceptedTime.realTime(),
                acceptedTime.dayTime()
        );
        data.addActiveQuest(runtime);

        boolean flagsChanged = false;
        for (String flag : definition.getFlagsToSetOnAccept()) {
            data.setFlag(flag);
            flagsChanged = true;
        }
        for (String flag : firstPhase.getFlagsToSetOnEnter()) {
            data.setFlag(flag);
            flagsChanged = true;
        }

        registerPhaseObjectives(player, definition, firstPhase);
        QuestMarkerService.refreshQuestMarkers(player, data, runtime, definition);

        ActivationContext activation = new ActivationContext();
        tryAutoEnterPhases(player, data, runtime, definition, firstPhase.getPhaseId(), activation);
        processImmediatelySatisfiedPhases(player, data, runtime, definition);
        flagsChanged = flagsChanged || activation.flagsChanged;

        syncQuestStateAndPush(player, runtime);
        if (flagsChanged) syncFlagsVarsAndPush(player, data);
        return QuestRejectCodeDictionary.Code.OK;
    }

    private static void publishQuestAccepted(QuestAcceptanceContext context) {
        ResourceLocation questId = ResourceLocation.parse(context.questId());
        QuestEventBus.fire(QuestChangeEvent.questAccepted(questId));
        MinecraftForge.EVENT_BUS.post(new QuestAcceptedEvent(context.player(), questId));
        MinecraftForge.EVENT_BUS.post(new QuestStartedEvent(context.player(), questId));
        playChapterSound(context.player(), context.definition().getChapterStartSound());
    }

    private static boolean evaluateCondition(ICondition condition,
                                             ServerPlayer player,
                                             Set<ResourceLocation> completedQuests,
                                             ArcQuestPlayer data) {
        return CoreProcessors.get().conditions().evaluate(condition, new QuestConditionContext(
                player, completedQuests, data.getAllFlags(), data.getAllVariables()));
    }

    // ═══════════════════════════════════════════════════════
    // enterCondition / 激活辅助
    // ═══════════════════════════════════════════════════════
    private static boolean activatePhase(ServerPlayer player,
                                         ArcQuestPlayer data,
                                         QuestRuntimeData qdata,
                                         QuestDefinition def,
                                         String fromPhaseId,
                                         String targetPhaseId,
                                         boolean enforceEnterCondition,
                                         ActivationContext ctx) {
        PhaseDefinition next = def.getPhase(targetPhaseId);
        if (next == null) return false;
        if (qdata.isPhaseActive(targetPhaseId) || qdata.isPhaseCompleted(targetPhaseId)) return false;

        if (enforceEnterCondition && !canEnterPhase(player, data, next, qdata)) {
            return false;
        }

        qdata.activatePhase(next.getPhaseId(), next.getObjectives().size());
        registerPhaseObjectives(player, def, next);

        for (String flag : next.getFlagsToSetOnEnter()) {
            data.setFlag(flag);
            ctx.flagsChanged = true;
        }

        ctx.activatedCount++;

        QuestEventBus.fire(QuestChangeEvent.phaseChanged(def.getId(), fromPhaseId, next.getPhaseId()));
        MinecraftForge.EVENT_BUS.post(new QuestPhaseChangedEvent(player, def.getId(), fromPhaseId, next.getPhaseId()));
        MinecraftForge.EVENT_BUS.post(new QuestPhaseActivatedEvent(player, def.getId(), fromPhaseId, next.getPhaseId(), !enforceEnterCondition));
        return true;
    }

    private static int tryAutoEnterPhases(ServerPlayer player,
                                          ArcQuestPlayer data,
                                          QuestRuntimeData qdata,
                                          QuestDefinition def,
                                          String fromPhaseId,
                                          ActivationContext ctx) {
        int before = ctx.activatedCount;
        boolean changed;

        do {
            changed = false;

            for (String pid : def.getPhaseIds()) {
                if (qdata.isPhaseActive(pid) || qdata.isPhaseCompleted(pid)) continue;

                PhaseDefinition phase = def.getPhase(pid);
                if (phase == null) continue;
                if (!phase.isAutoEnterByCondition()) continue;
                if (phase.getEnterCondition() == null) continue;
                if (!canEnterPhase(player, data, phase, qdata)) continue;

                boolean ok = activatePhase(player, data, qdata, def, fromPhaseId, pid, false, ctx);
                if (ok) changed = true;
            }
        } while (changed);

        return ctx.activatedCount - before;
    }

    private static final class ActivationContext {
        int activatedCount = 0;
        boolean flagsChanged = false;
        boolean needsQuestStateSync = false;
        boolean needsFlagsVarsSync = false;
    }

    private record QuestAcceptanceContext(ServerPlayer player, ArcQuestPlayer data,
                                          QuestDefinition definition, String questId,
                                          PhaseDefinition initialPhase,
                                          QuestConditionContext conditionContext) {
    }
}

