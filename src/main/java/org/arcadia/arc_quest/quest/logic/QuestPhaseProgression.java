package org.arcadia.arc_quest.quest.logic;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import org.arcadia.arc_quest.api.event.quest.QuestPhaseCompletedEvent;
import org.arcadia.arc_quest.api.event.quest.QuestProgressChangedEvent;
import org.arcadia.arc_quest.core.CoreProcessors;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.PhaseTransition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.event.QuestChangeEvent;
import org.arcadia.arc_quest.quest.event.QuestEventBus;
import org.arcadia.arc_quest.quest.logic.QuestPhaseActivation.ActivationContext;
import org.arcadia.arc_quest.quest.network.QuestRejectCodeDictionary;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.service.TrackedQuestService;
import org.arcadia.arc_quest.questmarker.api.MarkTrigger;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import static org.arcadia.arc_quest.quest.logic.QuestObjectiveService.registerPhaseObjectives;
import static org.arcadia.arc_quest.quest.logic.QuestObjectiveService.resolveRequiredCount;
import static org.arcadia.arc_quest.quest.logic.QuestObjectiveService.unregisterPhaseObjectives;
import static org.arcadia.arc_quest.quest.logic.QuestPhaseActivation.activatePhase;
import static org.arcadia.arc_quest.quest.logic.QuestPhaseActivation.tryAutoEnterPhases;
import static org.arcadia.arc_quest.quest.logic.QuestProgressEffects.evaluateCondition;
import static org.arcadia.arc_quest.quest.logic.QuestProgressRules.shouldCompleteQuest;
import static org.arcadia.arc_quest.quest.network.QuestSyncCoordinator.syncDeltaProgressAndPush;
import static org.arcadia.arc_quest.quest.network.QuestSyncCoordinator.syncFlagsVarsAndPush;
import static org.arcadia.arc_quest.quest.network.QuestSyncCoordinator.syncQuestStateAndPush;

/**
 * 普通任务的目标与阶段推进；对象仅持有无状态协作者，不持有玩家或世界引用。
 */
final class QuestPhaseProgression {
    private final QuestLifecycleService lifecycle;
    private final QuestLifecycleService.RewardGrant rewards;

    QuestPhaseProgression(QuestLifecycleService lifecycle, QuestLifecycleService.RewardGrant rewards) {
        this.lifecycle = Objects.requireNonNull(lifecycle);
        this.rewards = Objects.requireNonNull(rewards);
    }

    void incrementObjective(ServerPlayer player,
            String questId,
            String phaseId,
            int objIndex,
            int amount) {
        incrementObjective(player, questId, phaseId, objIndex, amount, 0);
    }

    void incrementObjective(ServerPlayer player, String questId, String phaseId, int objIndex, int amount, int resolvedRequired) {
        if (amount <= 0) return;

        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;
        QuestRuntimeData qdata = data.getActiveQuest(questId);
        if (qdata == null || qdata.getState() != QuestState.ACTIVE) return;

        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) return;
        if (def.isCollectionQuest() && !def.hasCollectionSheets()) return;
        if (!qdata.isPhaseActive(phaseId)) return;

        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase == null) return;
        if (objIndex < 0 || objIndex >= phase.getObjectives().size()) return;

        ObjectiveEntry objEntry = phase.getObjectives().get(objIndex);
        int required = resolvedRequired > 0 ? resolvedRequired : resolveRequiredCount(player, objEntry, data);
        boolean requirementChanged = qdata.setRequiredCount(phaseId, objIndex, required);

        int currentProgress = qdata.getObjectiveProgress(phaseId, objIndex);
        if (currentProgress >= required) {
            if (requirementChanged) syncDeltaProgressAndPush(player, questId, phaseId, objIndex, currentProgress);
            // A lowered dynamic threshold can make existing progress sufficient.
            checkPhaseCompletion(player, data, qdata, def, phaseId, false, objIndex, required);
            return;
        }

        int newProgress = qdata.incrementProgress(phaseId, objIndex, amount, required);

        if (currentProgress < required && newProgress >= required) {
            QuestMarkerTriggerService.triggerObjective(
                    player, data, qdata, phase, objIndex, MarkTrigger.OBJECTIVE_COMPLETED);
        }

        syncDeltaProgressAndPush(player, questId, phaseId, objIndex, newProgress);

        QuestEventBus.fire(QuestChangeEvent.objectiveProgressed(
                ResourceLocation.parse(questId), objIndex, newProgress, required));

        MinecraftForge.EVENT_BUS.post(new QuestProgressChangedEvent(
                player, ResourceLocation.parse(questId), phaseId,
                objIndex, currentProgress, newProgress, required));

        checkPhaseCompletion(player, data, qdata, def, phaseId, false, objIndex, required);
    }

    void checkPhaseCompletion(ServerPlayer player, ArcQuestPlayer data,
            QuestRuntimeData qdata, QuestDefinition def, String phaseId) {
        checkPhaseCompletion(player, data, qdata, def, phaseId, false);
    }

    void checkPhaseCompletion(ServerPlayer player, ArcQuestPlayer data,
            QuestRuntimeData qdata, QuestDefinition def, String phaseId,
            boolean forceAdvance) {
        checkPhaseCompletion(player, data, qdata, def, phaseId, forceAdvance, -1, 0);
    }

    private void checkPhaseCompletion(ServerPlayer player, ArcQuestPlayer data,
            QuestRuntimeData qdata, QuestDefinition def, String phaseId,
            boolean forceAdvance, int resolvedIndex, int resolvedRequired) {
        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase == null || qdata.getState() != QuestState.ACTIVE || !qdata.isPhaseActive(phaseId)) return;
        if (qdata.isPhasePendingManualAdvance(phaseId) && !forceAdvance) return;

        if (!qdata.beginPhaseCompletion(phaseId)) return;
        try {
            completeSatisfiedPhase(player, data, qdata, def, phaseId, phase, forceAdvance, resolvedIndex, resolvedRequired);
        } finally {
            qdata.endPhaseCompletion(phaseId);
        }
    }

    private void completeSatisfiedPhase(ServerPlayer player, ArcQuestPlayer data, QuestRuntimeData qdata,
            QuestDefinition def, String phaseId, PhaseDefinition phase,
            boolean forceAdvance, int resolvedIndex, int resolvedRequired) {

        int beforeBindings = phase.hasCollectionSheet() ? qdata.getOrCreateCollectionData().getCompletedBindingCount(phaseId) : 0;
        if (!requirementsSatisfied(player, data, qdata, def, phase, resolvedIndex, resolvedRequired)) {
            qdata.setPhaseCompletionCached(phaseId, false);
            if (phase.hasCollectionSheet() && qdata.getCollectionData().getCompletedBindingCount(phaseId) != beforeBindings)
                syncQuestStateAndPush(player, qdata);
            return;
        }

        if (qdata.getState() != QuestState.ACTIVE || !qdata.isPhaseActive(phaseId)) return;

        MinecraftForge.EVENT_BUS.post(new QuestPhaseCompletedEvent(
                player, ResourceLocation.parse(qdata.getQuestId()), phase.getPhaseId()));
        if (qdata.getState() != QuestState.ACTIVE || !qdata.isPhaseActive(phaseId)) return;
        rewards.grant(player, phase.getPhaseRewards(), "phase");
        if (qdata.getState() != QuestState.ACTIVE || !qdata.isPhaseActive(phaseId)) return;
        unregisterPhaseObjectives(player, def, phase);

        ActivationContext ctx = new ActivationContext();
        for (String flag : phase.getFlagsToSetOnComplete()) {
            data.setFlag(flag);
            ctx.flagsChanged = true;
        }
        QuestMarkerTriggerService.triggerPhase(
                player, data, qdata, phase, MarkTrigger.PHASE_COMPLETED);

        if ((!phase.shouldAutoAdvanceOnComplete() || (def.hasCollectionSheets() && phase.hasChoices())) && !forceAdvance) {
            qdata.markPhasePendingManualAdvance(phaseId);
            QuestMarkerService.refreshQuestMarkers(player, data, qdata, def);
            syncQuestStateAndPush(player, qdata);
            if (ctx.flagsChanged) {
                syncFlagsVarsAndPush(player, data);
            }
            return;
        }

        qdata.completePhase(phaseId);
        QuestMarkerTriggerService.triggerPhase(
                player, data, qdata, phase, MarkTrigger.PHASE_ADVANCED);

        finishPhaseTransition(player, data, qdata, def, phaseId, phase, ctx);
    }

    private boolean requirementsSatisfied(ServerPlayer player, ArcQuestPlayer data, QuestRuntimeData qdata,
                                           QuestDefinition def, PhaseDefinition phase, int resolvedIndex, int resolvedRequired) {
        String phaseId = phase.getPhaseId();
        java.util.Set<String> bound = new java.util.HashSet<>();
        if (phase.hasCollectionSheet()) {
            CollectionSheetService.initialize(def, qdata, data.getCollectionRecords());
            boolean ready = CollectionSheetService.satisfied(def, phase, qdata, data.getCollectionRecords());
            boolean rewardsChanged = CollectionEntryRewardService.updateBindings(player, data, def, phase, qdata);
            rewardsChanged |= ModernCollectionRewards.updateRewards(player, data, def, qdata);
            if (rewardsChanged) syncQuestStateAndPush(player, qdata);
            if (!ready) return false;
            phase.getCollectionSheet().getBindings().forEach(b -> bound.addAll(b.getObjectiveIds()));
        }
        List<ObjectiveEntry> objectives = phase.getObjectives();
        for (int i = 0; i < objectives.size(); i++) {
            ObjectiveEntry objective = objectives.get(i);
            if (objective.getType().equals(ObjectiveType.NULL) || (phase.hasCollectionSheet()
                    && (objective.isOptional() || bound.contains(objective.getObjectiveId())))) {
                continue;
            }
            int required = i == resolvedIndex ? resolvedRequired : resolveRequiredCount(player, objective, data);
            qdata.setRequiredCount(phaseId, i, required);
            if (qdata.getObjectiveProgress(phaseId, i) < required) {
                return false;
            }
        }
        return true;
    }

    private void finishPhaseTransition(ServerPlayer player, ArcQuestPlayer data, QuestRuntimeData qdata,
                                       QuestDefinition def, String phaseId, PhaseDefinition phase, ActivationContext ctx) {
        // choices：该 phase 完成后等待玩家选路，不自动推进 transition
        if (phase.hasChoices()) {
            // 但允许 auto enter phase 扫描（如配置了 autoEnterByCondition=true）
            tryAutoEnterPhases(player, data, qdata, def, phaseId, ctx);

            if (shouldCompleteQuest(def, qdata)) {
                lifecycle.completeQuest(player, data, qdata, def);
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

            activatePhase(player, data, qdata, def, phaseId, tr.selectTargetPhaseId(player.getRandom()), true, ctx);
        }

        // enterCondition 自动扫描（仅 autoEnterByCondition=true 的 phase）
        tryAutoEnterPhases(player, data, qdata, def, phaseId, ctx);
        processImmediatelySatisfiedPhases(player, data, qdata, def);

        if (shouldCompleteQuest(def, qdata)) {
            lifecycle.completeQuest(player, data, qdata, def);
        } else {
            syncQuestStateAndPush(player, qdata);
            if (ctx.flagsChanged) {
                syncFlagsVarsAndPush(player, data);
            }
        }
    }

    void advanceToPhase(ServerPlayer player,
            ArcQuestPlayer data,
            QuestRuntimeData qdata,
            QuestDefinition def,
            String nextPhaseId) {
        String fromPhaseId = qdata.getCurrentPhaseId();

        ActivationContext ctx = new ActivationContext();
        boolean ok = activatePhase(player, data, qdata, def, fromPhaseId, nextPhaseId, true, ctx);
        if (!ok) {
            ArcQuestLog.warn(ArcQuestLog.Category.QUEST_PROGRESS, "Target phase cannot be activated: {}/{}", def.getId(), nextPhaseId);
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

    QuestRejectCodeDictionary.Code confirmManualPhaseAdvance(ServerPlayer player,
            String questId,
            String phaseId) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return QuestRejectCodeDictionary.Code.NOT_ACTIVE;
        QuestRuntimeData qdata = data.getActiveQuest(questId);
        if (qdata == null || phaseId == null || phaseId.isEmpty() || !qdata.isPhasePendingManualAdvance(phaseId)) {
            return QuestRejectCodeDictionary.Code.PHASE_NOT_FOUND;
        }
        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) return QuestRejectCodeDictionary.Code.QUEST_NOT_FOUND;
        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase == null) return QuestRejectCodeDictionary.Code.PHASE_NOT_FOUND;
        if (def.hasCollectionSheets() && phase.hasChoices()) return QuestRejectCodeDictionary.Code.CHOICE_CONDITION_NOT_MET;
        qdata.clearPhasePendingManualAdvance(phaseId);
        qdata.completePhase(phaseId);
        QuestMarkerTriggerService.triggerPhase(
                player, data, qdata, phase, MarkTrigger.PHASE_ADVANCED);
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
            if (ok) activatePhase(player, data, qdata, def, phaseId, tr.selectTargetPhaseId(player.getRandom()), true, ctx);
        }
        tryAutoEnterPhases(player, data, qdata, def, phaseId, ctx);
        processImmediatelySatisfiedPhases(player, data, qdata, def);
        if (shouldCompleteQuest(def, qdata)) {
            lifecycle.completeQuest(player, data, qdata, def);
        } else {
            QuestMarkerService.refreshQuestMarkers(player, data, qdata, def);
            syncQuestStateAndPush(player, qdata);
        }
        if (ctx.flagsChanged) syncFlagsVarsAndPush(player, data);
        return QuestRejectCodeDictionary.Code.OK;
    }

    QuestRejectCodeDictionary.Code abandonPhase(
            ServerPlayer player, String questId, String phaseId) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return QuestRejectCodeDictionary.Code.NOT_ACTIVE;
        QuestRuntimeData qdata = data.getActiveQuest(questId);
        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) return QuestRejectCodeDictionary.Code.QUEST_NOT_FOUND;
        if (qdata == null || qdata.getState() != QuestState.ACTIVE) {
            return QuestRejectCodeDictionary.Code.NOT_ACTIVE;
        }
        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase == null || !qdata.isPhaseActive(phaseId)) {
            return QuestRejectCodeDictionary.Code.PHASE_NOT_FOUND;
        }
        unregisterPhaseObjectives(player, def, phase);
        if (!qdata.abandonPhase(phaseId)) return QuestRejectCodeDictionary.Code.PHASE_NOT_FOUND;
        QuestMarkerService.refreshQuestMarkers(player, data, qdata, def);
        syncQuestStateAndPush(player, qdata);
        return QuestRejectCodeDictionary.Code.OK;
    }

    void forceCompletePhaseInternal(ServerPlayer player, ArcQuestPlayer data,
            QuestRuntimeData qdata, QuestDefinition def,
            String phaseId) {
        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase == null || !qdata.isPhaseActive(phaseId)) return;

        qdata.clearPhasePendingManualAdvance(phaseId);
        if (phase.hasCollectionSheet()) {
            CollectionSheetService.initialize(def, qdata, data.getCollectionRecords());
            phase.getCollectionSheet().getBindings().forEach(b ->
                    qdata.getOrCreateCollectionData().markBindingComplete(phaseId, b.getBindingId()));
        }
        for (int i = 0; i < phase.getObjectives().size(); i++) {
            int required = resolveRequiredCount(player, phase.getObjectives().get(i), data);
            qdata.setRequiredCount(phaseId, i, required);
            int previous = qdata.getObjectiveProgress(phaseId, i);
            qdata.setObjectiveProgress(phaseId, i, required);
            if (previous < required) {
                QuestMarkerTriggerService.triggerObjective(
                        player, data, qdata, phase, i, MarkTrigger.OBJECTIVE_COMPLETED);
            }
        }
        qdata.invalidatePhaseCache();
        checkPhaseCompletion(player, data, qdata, def, phaseId, true);
    }

    void processImmediatelySatisfiedPhases(ServerPlayer player,
            ArcQuestPlayer data,
            QuestRuntimeData qdata,
            QuestDefinition def) {
        if (qdata.getState() != QuestState.ACTIVE) return;
        boolean changed;
        do {
            changed = false;
            for (String phaseId : List.copyOf(qdata.getActivePhaseIds())) {
                PhaseDefinition phase = def.getPhase(phaseId);
                if (phase == null || qdata.isPhasePendingManualAdvance(phaseId)) continue;

                if (!phase.hasCollectionSheet() && qdata.isPhaseCompletionCached(phaseId)) {
                    if (!qdata.isPhaseCompletionSatisfied(phaseId)) continue;
                }

                int beforeCompleted = qdata.getCompletedPhaseIds().size();
                int beforePending = qdata.getPendingManualAdvancePhaseIds().size();
                checkPhaseCompletion(player, data, qdata, def, phaseId);
                if (qdata.getCompletedPhaseIds().size() != beforeCompleted || qdata.getPendingManualAdvancePhaseIds().size() != beforePending) {
                    changed = true;
                }
            }
        } while (changed && qdata.getState() == QuestState.ACTIVE);
    }

    QuestRejectCodeDictionary.Code applyQuestAcceptance(ServerPlayer player, ArcQuestPlayer data,
            QuestDefinition definition, String questId,
            PhaseDefinition firstPhase) {
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
        CollectionSheetService.initialize(definition, runtime, data.getCollectionRecords());

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
        QuestMarkerTriggerService.triggerQuest(
                player, data, runtime, definition, MarkTrigger.QUEST_ACCEPTED);
        QuestMarkerTriggerService.triggerPhase(
                player, data, runtime, firstPhase, MarkTrigger.PHASE_ENTERED);

        ActivationContext activation = new ActivationContext();
        tryAutoEnterPhases(player, data, runtime, definition, firstPhase.getPhaseId(), activation);
        processImmediatelySatisfiedPhases(player, data, runtime, definition);
        flagsChanged = flagsChanged || activation.flagsChanged;

        syncQuestStateAndPush(player, runtime);
        TrackedQuestService.onQuestAccepted(player, questId);
        if (flagsChanged) syncFlagsVarsAndPush(player, data);
        return QuestRejectCodeDictionary.Code.OK;
    }
}
