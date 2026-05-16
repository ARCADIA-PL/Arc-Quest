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
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.capability.IQuestCapability;
import org.arcadia.arc_quest.quest.capability.QuestCapabilityProvider;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.event.QuestChangeEvent;
import org.arcadia.arc_quest.quest.event.QuestEventBus;
import org.arcadia.arc_quest.quest.logic.profile.CollectionEntryUpdateResult;
import org.arcadia.arc_quest.quest.logic.profile.CollectionQuestEngine;
import org.arcadia.arc_quest.quest.logic.profile.CollectionVisibilityUpdateResult;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.QuestRejectCodeDictionary;
import org.arcadia.arc_quest.quest.network.QuestSyncCoordinator;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.tracking.ObjectiveKey;
import org.arcadia.arc_quest.quest.tracking.ObjectiveTracker;
import org.arcadia.arc_quest.quest.tracking.TrackedObjective;
import org.slf4j.Logger;

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

        if (def.isCollectionQuest()) {
            return CollectionQuestEngine.acceptQuest(player, cap, def);
        }

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

        long acceptedTick = player.getServer() != null ? player.getServer().getTickCount() : 0L;
        long acceptedRealMs = System.currentTimeMillis();
        long acceptedDayTime = player.level().getDayTime() % 24000L;

        QuestRuntimeData data = new QuestRuntimeData(
                questId,
                firstPhase.getPhaseId(),
                firstPhase.getObjectives().size(),
                acceptedTick,
                acceptedRealMs,
                acceptedDayTime
        );
        cap.addActiveQuest(data);

        boolean flagsChanged = false;
        for (String flag : def.getFlagsToSetOnAccept()) {
            cap.setFlag(flag);
            flagsChanged = true;
        }
        for (String flag : firstPhase.getFlagsToSetOnEnter()) {
            cap.setFlag(flag);
            flagsChanged = true;
        }

        registerPhaseObjectives(player, def, firstPhase);
        QuestMarkerService.refreshQuestMarkers(player, cap, data, def);

        // 仅对 autoEnterByCondition=true 的 phase 扫描自动入场
        ActivationContext ctx = new ActivationContext();
        tryAutoEnterPhases(player, cap, data, def, firstPhase.getPhaseId(), ctx);
        processImmediatelySatisfiedPhases(player, cap, data, def);
        flagsChanged = flagsChanged || ctx.flagsChanged;

        syncQuestStateAndPush(player, data);
        if (flagsChanged) {
            syncFlagsVarsAndPush(player, cap);
        }

        QuestEventBus.fire(QuestChangeEvent.questAccepted(ResourceLocation.parse(questId)));
        MinecraftForge.EVENT_BUS.post(new QuestAcceptedEvent(player, ResourceLocation.parse(questId)));
        MinecraftForge.EVENT_BUS.post(new QuestStartedEvent(player, ResourceLocation.parse(questId)));
        playChapterSound(player, def.getChapterStartSound());

        return QuestRejectCodeDictionary.Code.OK;
    }

    public static void incrementObjective(ServerPlayer player,
                                          String questId,
                                          String phaseId,
                                          int objIndex,
                                          int amount) {
        if (amount <= 0) return;

        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data == null || data.getState() != QuestState.ACTIVE) return;

        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) return;
        if (def.isCollectionQuest()) return;
        if (!data.isPhaseActive(phaseId)) return;

        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase == null) return;
        if (objIndex < 0 || objIndex >= phase.getObjectives().size()) return;

        ObjectiveEntry objEntry = phase.getObjectives().get(objIndex);
        int required = resolveRequiredCount(player, objEntry, cap);

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

    public static void incrementCollectionEntry(ServerPlayer player,
                                                String questId,
                                                String phaseId,
                                                int amount) {
        if (amount <= 0) return;

        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data == null || data.getState() != QuestState.ACTIVE || !data.hasCollectionData()) return;

        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null || !def.isCollectionQuest()) return;

        CollectionEntryUpdateResult result = CollectionQuestEngine.incrementEntryWithResult(player, cap, def, data, phaseId, amount);
        if (result.isChanged()) {
            syncQuestStateAndPush(player, data);
        }
    }

    public static void revealCollectionEntry(ServerPlayer player,
                                             String questId,
                                             String phaseId) {
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data == null || data.getState() != QuestState.ACTIVE || !data.hasCollectionData()) return;

        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null || !def.isCollectionQuest()) return;

        CollectionVisibilityUpdateResult result = CollectionQuestEngine.revealEntry(def, data, phaseId);
        if (result.isChanged()) {
            syncQuestStateAndPush(player, data);
        }
    }

    public static void refreshCollectionVisibility(ServerPlayer player, String questId) {
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data == null || data.getState() != QuestState.ACTIVE || !data.hasCollectionData()) return;

        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null || !def.isCollectionQuest()) return;

        if (CollectionQuestEngine.refreshVisibility(player, cap, def, data) > 0) {
            syncQuestStateAndPush(player, data);
        }
    }

    public static void discoverCollectionEntry(ServerPlayer player,
                                               String questId,
                                               String phaseId) {
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data == null || data.getState() != QuestState.ACTIVE || !data.hasCollectionData()) return;

        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null || !def.isCollectionQuest()) return;

        CollectionEntryUpdateResult result = CollectionQuestEngine.discoverEntryWithResult(def, data, phaseId);
        if (result.isChanged()) {
            syncQuestStateAndPush(player, data);
        }
    }

    public static void addCollectionUniqueKey(ServerPlayer player,
                                              String questId,
                                              String phaseId,
                                              String uniqueKey) {
        if (uniqueKey == null || uniqueKey.isEmpty()) return;

        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data == null || data.getState() != QuestState.ACTIVE || !data.hasCollectionData()) return;

        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null || !def.isCollectionQuest()) return;

        CollectionEntryUpdateResult result = CollectionQuestEngine.addUniqueProgressWithResult(player, cap, def, data, phaseId, uniqueKey);
        if (result.isChanged()) {
            syncQuestStateAndPush(player, data);
        }
    }

    // ═══════════════════════════════════════════════════════
    //  目标推进（并行 phase 维度）
    // ═══════════════════════════════════════════════════════
    private static void checkPhaseCompletion(ServerPlayer player,
                                             IQuestCapability cap,
                                             QuestRuntimeData data,
                                             QuestDefinition def,
                                             String phaseId) {
        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase == null || !data.isPhaseActive(phaseId)) return;

        List<ObjectiveEntry> objectives = phase.getObjectives();
        for (int i = 0; i < objectives.size(); i++) {
            ObjectiveEntry objective = objectives.get(i);
            if (objective.getType() == ObjectiveType.NULL) {
                continue;
            }
            if (data.getObjectiveProgress(phaseId, i) < resolveRequiredCount(player, objective, cap)) {
                return;
            }
        }

        MinecraftForge.EVENT_BUS.post(new QuestPhaseCompletedEvent(
                player, ResourceLocation.parse(data.getQuestId()), phase.getPhaseId()));

        grantRewards(player, phase.getPhaseRewards(), "phase");
        unregisterPhaseObjectives(player, def, phase);

        ActivationContext ctx = new ActivationContext();
        for (String flag : phase.getFlagsToSetOnComplete()) {
            cap.setFlag(flag);
            ctx.flagsChanged = true;
        }

        if (!phase.shouldAutoAdvanceOnComplete()) {
            data.markPhasePendingManualAdvance(phaseId);
            QuestMarkerService.refreshQuestMarkers(player, cap, data, def);
            syncQuestStateAndPush(player, data);
            if (ctx.flagsChanged) {
                syncFlagsVarsAndPush(player, cap);
            }
            return;
        }

        data.completePhase(phaseId);

        // choices：该 phase 完成后等待玩家选路，不自动推进 transition
        if (phase.hasChoices()) {
            // 但允许 auto enter phase 扫描（如配置了 autoEnterByCondition=true）
            tryAutoEnterPhases(player, cap, data, def, phaseId, ctx);

            if (shouldCompleteQuest(def, data)) {
                completeQuest(player, cap, data, def);
                return;
            }

            QuestMarkerService.refreshQuestMarkers(player, cap, data, def);
            syncQuestStateAndPush(player, data);
            if (ctx.flagsChanged) {
                syncFlagsVarsAndPush(player, cap);
            }
            return;
        }

        Set<ResourceLocation> completedQuests = cap.getCompletedQuestLocations();

        // 自动解锁后继（可多条，支持 thenGoToIf）
        for (PhaseTransition tr : phase.getTransitions()) {
            boolean ok = tr.getCondition() == null
                    || tr.getCondition().test(player, completedQuests, cap.getAllFlags(), cap.getAllVariables());
            if (!ok) continue;

            activatePhase(player, cap, data, def, phaseId, tr.getTargetPhaseId(), true, ctx);
        }

        // enterCondition 自动扫描（仅 autoEnterByCondition=true 的 phase）
        tryAutoEnterPhases(player, cap, data, def, phaseId, ctx);
        processImmediatelySatisfiedPhases(player, cap, data, def);

        if (shouldCompleteQuest(def, data)) {
            completeQuest(player, cap, data, def);
        } else {
            syncQuestStateAndPush(player, data);
            if (ctx.flagsChanged) {
                syncFlagsVarsAndPush(player, cap);
            }
        }
    }

    public static void advanceToPhase(ServerPlayer player,
                                      IQuestCapability cap,
                                      QuestRuntimeData data,
                                      QuestDefinition def,
                                      String nextPhaseId) {
        String fromPhaseId = data.getCurrentPhaseId();

        ActivationContext ctx = new ActivationContext();
        boolean ok = activatePhase(player, cap, data, def, fromPhaseId, nextPhaseId, true, ctx);
        if (!ok) {
            LOGGER.warn("[ArcQuest] Target phase cannot be activated: {}/{}", def.getId(), nextPhaseId);
            return;
        }

        tryAutoEnterPhases(player, cap, data, def, fromPhaseId, ctx);
        processImmediatelySatisfiedPhases(player, cap, data, def);

        QuestMarkerService.refreshQuestMarkers(player, cap, data, def);
        syncQuestStateAndPush(player, data);
        if (ctx.flagsChanged) {
            syncFlagsVarsAndPush(player, cap);
        }
    }

    // ═══════════════════════════════════════════════════════
    // 阶段推进（显式推进，用于 choice 等）
    // ═══════════════════════════════════════════════════════
    public static QuestRejectCodeDictionary.Code confirmManualPhaseAdvance(ServerPlayer player,
                                                                           String questId,
                                                                           String phaseId) {
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data == null) return QuestRejectCodeDictionary.Code.NOT_ACTIVE;
        if (phaseId == null || phaseId.isEmpty() || !data.isPhasePendingManualAdvance(phaseId)) {
            return QuestRejectCodeDictionary.Code.PHASE_NOT_FOUND;
        }
        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) return QuestRejectCodeDictionary.Code.QUEST_NOT_FOUND;
        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase == null) return QuestRejectCodeDictionary.Code.PHASE_NOT_FOUND;
        data.clearPhasePendingManualAdvance(phaseId);
        data.completePhase(phaseId);
        ActivationContext ctx = new ActivationContext();
        for (String flag : phase.getFlagsToSetOnComplete()) {
            cap.setFlag(flag);
            ctx.flagsChanged = true;
        }
        if (phase.hasChoices()) {
            tryAutoEnterPhases(player, cap, data, def, phaseId, ctx);
            QuestMarkerService.refreshQuestMarkers(player, cap, data, def);
            syncQuestStateAndPush(player, data);
            if (ctx.flagsChanged) syncFlagsVarsAndPush(player, cap);
            return QuestRejectCodeDictionary.Code.OK;
        }
        Set<ResourceLocation> completedQuests = cap.getCompletedQuestLocations();
        for (PhaseTransition tr : phase.getTransitions()) {
            boolean ok = tr.getCondition() == null || tr.getCondition().test(player, completedQuests, cap.getAllFlags(), cap.getAllVariables());
            if (ok) activatePhase(player, cap, data, def, phaseId, tr.getTargetPhaseId(), true, ctx);
        }
        tryAutoEnterPhases(player, cap, data, def, phaseId, ctx);
        processImmediatelySatisfiedPhases(player, cap, data, def);
        if (shouldCompleteQuest(def, data)) {
            completeQuest(player, cap, data, def);
        } else {
            QuestMarkerService.refreshQuestMarkers(player, cap, data, def);
            syncQuestStateAndPush(player, data);
        }
        if (ctx.flagsChanged) syncFlagsVarsAndPush(player, cap);
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

        ActivationContext ctx = new ActivationContext();

        String flagToSet = chosen.getFlagToSet();
        if (flagToSet != null && !flagToSet.isEmpty()) {
            cap.setFlag(flagToSet);
            ctx.flagsChanged = true;
            LOGGER.debug("[ArcQuest] Set flag '{}' from choice", flagToSet);
        }

        String targetPhaseId = chosen.getTargetPhaseId();
        if (targetPhaseId == null || targetPhaseId.isEmpty()) {
            LOGGER.warn("[ArcQuest] Choice has no target phase: {}", choiceIndex);
            return QuestRejectCodeDictionary.Code.CHOICE_TARGET_PHASE_MISSING;
        }

        // 完成当前 choice phase
        data.completePhase(resolvedPhaseId);
        unregisterPhaseObjectives(player, def, currentPhase);
        for (String flag : currentPhase.getFlagsToSetOnComplete()) {
            cap.setFlag(flag);
            ctx.flagsChanged = true;
        }

        // 并行增强：激活"选择目标 + 当前 phase 里其它满足条件的 transition"
        Set<String> toActivate = new LinkedHashSet<>();
        toActivate.add(targetPhaseId);

        for (PhaseTransition tr : currentPhase.getTransitions()) {
            String pid = tr.getTargetPhaseId();
            if (pid == null || pid.isEmpty() || pid.equals(targetPhaseId)) continue;

            ICondition cond = tr.getCondition();
            boolean ok = cond == null || cond.test(player, completedQuests, cap.getAllFlags(), cap.getAllVariables());
            if (!ok) continue;

            toActivate.add(pid);
        }

        for (String pid : toActivate) {
            activatePhase(player, cap, data, def, resolvedPhaseId, pid, true, ctx);
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
        tryAutoEnterPhases(player, cap, data, def, resolvedPhaseId, ctx);
        processImmediatelySatisfiedPhases(player, cap, data, def);

        if (shouldCompleteQuest(def, data)) {
            completeQuest(player, cap, data, def);
        } else {
            QuestMarkerService.refreshQuestMarkers(player, cap, data, def);
            syncQuestStateAndPush(player, data);
            if (ctx.flagsChanged) {
                syncFlagsVarsAndPush(player, cap);
            }
        }

        if (ctx.activatedCount == 0 && !shouldCompleteQuest(def, data)) {
            LOGGER.warn("[ArcQuest] Choice resolved but no next phase activated: quest={}, phase={}, choice={}",
                    questId, resolvedPhaseId, choiceIndex);
            return QuestRejectCodeDictionary.Code.CHOICE_TARGET_PHASE_MISSING;
        }

        return QuestRejectCodeDictionary.Code.OK;
    }

    private static void completeQuest(ServerPlayer player,
                                      IQuestCapability cap,
                                      QuestRuntimeData data,
                                      QuestDefinition def) {
        doCompleteQuest(player, cap, data, def, "completed");
    }

    // ═══════════════════════════════════════════════════════
    // 任务完成 / 失败 / 放弃
    // ═══════════════════════════════════════════════════════
    public static void failQuest(ServerPlayer player, String questId) {
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data == null) return;

        data.setState(QuestState.FAILED);
        cap.markFailed(questId);
        ObjectiveTracker.INSTANCE.unregisterQuest(player.getUUID(), questId);
        QuestMarkerService.clearQuestMarkers(cap, questId);

        syncQuestStateAndPush(player, data);
        QuestEventBus.fire(QuestChangeEvent.questFailed(ResourceLocation.parse(questId)));
        MinecraftForge.EVENT_BUS.post(new QuestFailedEvent(player, ResourceLocation.parse(questId)));
        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def != null) playChapterSound(player, def.getChapterFailSound());
    }

    public static boolean abandonQuest(ServerPlayer player, String questId) {
        return abandonQuestWithCode(player, questId) == QuestRejectCodeDictionary.Code.OK;
    }

    public static QuestRejectCodeDictionary.Code abandonQuestWithCode(ServerPlayer player, String questId) {
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        if (!cap.isQuestActive(questId)) {
            return QuestRejectCodeDictionary.Code.NOT_ACTIVE;
        }

        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data != null) {
            data.setState(QuestState.FAILED);
        }

        cap.markFailed(questId);
        ObjectiveTracker.INSTANCE.unregisterQuest(player.getUUID(), questId);
        QuestMarkerService.clearQuestMarkers(cap, questId);

        syncFullDataAndPush(player, cap);
        QuestEventBus.fire(QuestChangeEvent.questFailed(ResourceLocation.parse(questId)));
        MinecraftForge.EVENT_BUS.post(new QuestFailedEvent(player, ResourceLocation.parse(questId)));
        MinecraftForge.EVENT_BUS.post(new QuestAbandonedEvent(player, ResourceLocation.parse(questId)));
        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def != null) playChapterSound(player, def.getChapterFailSound());
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
        QuestMarkerService.clearQuestMarkers(cap, questId);

        LOGGER.info("[ArcQuest] Player {} {} quest: {}",
                player.getGameProfile().getName(), logPrefix, questId);

        syncQuestStateAndPush(player, data);
        syncFlagsVarsAndPush(player, cap);
        QuestEventBus.fire(QuestChangeEvent.questCompleted(ResourceLocation.parse(questId)));
        MinecraftForge.EVENT_BUS.post(new QuestCompletedEvent(player, ResourceLocation.parse(questId)));
        playChapterSound(player, def.getChapterCompleteSound());
    }

    public static void syncToClient(ServerPlayer player, String questId) {
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data != null) {
            syncQuestStateAndPush(player, data);
        }
    }

    private static void playChapterSound(ServerPlayer player, SoundEvent sound) {
        if (player == null || sound == null) return;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1.0f, 1.0f);
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

    public static void rebuildTrackingIndex(ServerPlayer player, IQuestCapability cap) {
        ObjectiveTracker.INSTANCE.unregisterPlayer(player.getUUID());

        int activeQuestCount = 0;
        for (Map.Entry<String, QuestRuntimeData> entry : cap.getAllActiveQuests().entrySet()) {
            QuestRuntimeData data = entry.getValue();
            if (data.getState() != QuestState.ACTIVE) continue;
            activeQuestCount++;

            QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(data.getQuestId()));
            if (def == null) continue;

            for (String phaseId : data.getActivePhaseIds()) {
                PhaseDefinition phase = def.getPhase(phaseId);
                if (phase == null) continue;
                registerPhaseObjectives(player, def, phase);
            }

            QuestMarkerService.refreshQuestMarkers(player, cap, data, def);
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

    public static int resolveRequiredCount(ServerPlayer player, ObjectiveEntry obj, IQuestCapability cap) {
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
            variableValue = (var == null || var.isEmpty()) ? 0 : cap.getVariable(var);
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
                                                          IQuestCapability cap,
                                                          QuestRuntimeData data,
                                                          QuestDefinition def) {
        boolean changed;
        do {
            changed = false;
            for (String phaseId : List.copyOf(data.getActivePhaseIds())) {
                PhaseDefinition phase = def.getPhase(phaseId);
                if (phase == null) continue;

                if (data.isPhaseCompletionCached(phaseId)) {
                    if (!data.isPhaseCompletionSatisfied(phaseId)) continue;
                }

                boolean allSatisfied = true;
                for (int i = 0; i < phase.getObjectives().size(); i++) {
                    ObjectiveEntry objective = phase.getObjectives().get(i);
                    if (objective.getType() == ObjectiveType.NULL) continue;
                    if (data.getObjectiveProgress(phaseId, i) < resolveRequiredCount(player, objective, cap)) {
                        allSatisfied = false;
                        break;
                    }
                }
                if (!allSatisfied) {
                    data.setPhaseCompletionCached(phaseId, false);
                    continue;
                }
                int beforeCompleted = data.getCompletedPhaseIds().size();
                int beforePending = data.getPendingManualAdvancePhaseIds().size();
                checkPhaseCompletion(player, cap, data, def, phaseId);
                if (data.getCompletedPhaseIds().size() != beforeCompleted || data.getPendingManualAdvancePhaseIds().size() != beforePending) {
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
                                         IQuestCapability cap,
                                         PhaseDefinition phase) {
        ICondition cond = phase.getEnterCondition();
        if (cond == null) return true;
        return cond.test(player, cap.getCompletedQuestLocations(), cap.getAllFlags(), cap.getAllVariables());
    }

    // ═══════════════════════════════════════════════════════
    // enterCondition / 激活辅助
    // ═══════════════════════════════════════════════════════
    private static boolean activatePhase(ServerPlayer player,
                                         IQuestCapability cap,
                                         QuestRuntimeData data,
                                         QuestDefinition def,
                                         String fromPhaseId,
                                         String targetPhaseId,
                                         boolean enforceEnterCondition,
                                         ActivationContext ctx) {
        PhaseDefinition next = def.getPhase(targetPhaseId);
        if (next == null) return false;
        if (data.isPhaseActive(targetPhaseId) || data.isPhaseCompleted(targetPhaseId)) return false;

        if (enforceEnterCondition && !canEnterPhase(player, cap, next)) {
            return false;
        }

        data.activatePhase(next.getPhaseId(), next.getObjectives().size());
        registerPhaseObjectives(player, def, next);

        for (String flag : next.getFlagsToSetOnEnter()) {
            cap.setFlag(flag);
            ctx.flagsChanged = true;
        }

        ctx.activatedCount++;

        QuestEventBus.fire(QuestChangeEvent.phaseChanged(def.getId(), fromPhaseId, next.getPhaseId()));
        MinecraftForge.EVENT_BUS.post(new QuestPhaseChangedEvent(player, def.getId(), fromPhaseId, next.getPhaseId()));
        MinecraftForge.EVENT_BUS.post(new QuestPhaseActivatedEvent(player, def.getId(), fromPhaseId, next.getPhaseId(), !enforceEnterCondition));
        return true;
    }

    private static int tryAutoEnterPhases(ServerPlayer player,
                                          IQuestCapability cap,
                                          QuestRuntimeData data,
                                          QuestDefinition def,
                                          String fromPhaseId,
                                          ActivationContext ctx) {
        int before = ctx.activatedCount;
        boolean changed;

        do {
            changed = false;

            for (String pid : def.getPhaseIds()) {
                if (data.isPhaseActive(pid) || data.isPhaseCompleted(pid)) continue;

                PhaseDefinition phase = def.getPhase(pid);
                if (phase == null) continue;
                if (!phase.isAutoEnterByCondition()) continue;
                if (phase.getEnterCondition() == null) continue;
                if (!canEnterPhase(player, cap, phase)) continue;

                boolean ok = activatePhase(player, cap, data, def, fromPhaseId, pid, false, ctx);
                if (ok) changed = true;
            }
        } while (changed);

        return ctx.activatedCount - before;
    }

    private static final class ActivationContext {
        int activatedCount = 0;
        boolean flagsChanged = false;
    }
}

