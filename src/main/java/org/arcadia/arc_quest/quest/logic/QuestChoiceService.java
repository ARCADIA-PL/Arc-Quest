package org.arcadia.arc_quest.quest.logic;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import org.arcadia.arc_quest.api.event.quest.QuestChoiceResolvedEvent;
import org.arcadia.arc_quest.quest.api.ChoiceOption;
import org.arcadia.arc_quest.quest.api.ICondition;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.PhaseTransition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.QuestPhaseActivation.ActivationContext;
import org.arcadia.arc_quest.quest.network.QuestRejectCodeDictionary;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questmarker.api.MarkTrigger;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import static org.arcadia.arc_quest.quest.logic.QuestObjectiveService.unregisterPhaseObjectives;
import static org.arcadia.arc_quest.quest.logic.QuestPhaseActivation.activatePhase;
import static org.arcadia.arc_quest.quest.logic.QuestPhaseActivation.tryAutoEnterPhases;
import static org.arcadia.arc_quest.quest.logic.QuestProgressEffects.evaluateCondition;
import static org.arcadia.arc_quest.quest.logic.QuestProgressRules.shouldCompleteQuest;
import static org.arcadia.arc_quest.quest.network.QuestSyncCoordinator.syncFlagsVarsAndPush;
import static org.arcadia.arc_quest.quest.network.QuestSyncCoordinator.syncQuestStateAndPush;

/**
 * 玩家分支选择的校验与执行；阶段激活和任务收尾委托给各自的服务。
 */
final class QuestChoiceService {
    private final QuestPhaseProgression phases;
    private final QuestLifecycleService lifecycle;

    QuestChoiceService(QuestPhaseProgression phases, QuestLifecycleService lifecycle) {
        this.phases = Objects.requireNonNull(phases);
        this.lifecycle = Objects.requireNonNull(lifecycle);
    }

    QuestRejectCodeDictionary.Code handlePlayerChoiceWithCode(ServerPlayer player,
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
            ArcQuestLog.warn(ArcQuestLog.Category.QUEST_PROGRESS, "Invalid choice index {} for quest {} phase {}", choiceIndex, questId, resolvedPhaseId);
            return QuestRejectCodeDictionary.Code.INVALID_CHOICE_INDEX;
        }

        ChoiceOption chosen = choices.get(choiceIndex);

        Set<ResourceLocation> completedQuests = data.getCompletedQuestLocations();
        ICondition visibleCondition = chosen.getVisibleCondition();
        boolean conditionsMet = visibleCondition == null
                || evaluateCondition(visibleCondition, player, completedQuests, data);
        if (!conditionsMet) {
            ArcQuestLog.debug(ArcQuestLog.Category.QUEST_PROGRESS, "Choice conditions not met for index {}", choiceIndex);
            return QuestRejectCodeDictionary.Code.CHOICE_CONDITION_NOT_MET;
        }

        ActivationContext ctx = new ActivationContext();

        String flagToSet = chosen.getFlagToSet();
        if (flagToSet != null && !flagToSet.isEmpty()) {
            data.setFlag(flagToSet);
            ctx.flagsChanged = true;
            ArcQuestLog.debug(ArcQuestLog.Category.QUEST_PROGRESS, "Set flag '{}' from choice", flagToSet);
        }

        String targetPhaseId = chosen.getTargetPhaseId();
        if (targetPhaseId == null || targetPhaseId.isEmpty()) {
            ArcQuestLog.warn(ArcQuestLog.Category.QUEST_PROGRESS, "Choice has no target phase: {}", choiceIndex);
            return QuestRejectCodeDictionary.Code.CHOICE_TARGET_PHASE_MISSING;
        }

        // 完成当前 choice phase
        qdata.completePhase(resolvedPhaseId);
        unregisterPhaseObjectives(player, def, currentPhase);
        for (String flag : currentPhase.getFlagsToSetOnComplete()) {
            data.setFlag(flag);
            ctx.flagsChanged = true;
        }
        QuestMarkerTriggerService.triggerPhase(
                player, data, qdata, currentPhase, MarkTrigger.PHASE_COMPLETED);
        QuestMarkerTriggerService.triggerPhase(
                player, data, qdata, currentPhase, MarkTrigger.PHASE_ADVANCED);

        // 并行增强：激活"选择目标 + 当前 phase 里其它满足条件的 transition"
        Set<String> toActivate = new LinkedHashSet<>();
        toActivate.add(targetPhaseId);

        for (PhaseTransition tr : currentPhase.getTransitions()) {
            String pid = tr.selectTargetPhaseId(player.getRandom());
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
        phases.processImmediatelySatisfiedPhases(player, data, qdata, def);

        if (shouldCompleteQuest(def, qdata)) {
            lifecycle.completeQuest(player, data, qdata, def);
        } else {
            QuestMarkerService.refreshQuestMarkers(player, data, qdata, def);
            syncQuestStateAndPush(player, qdata);
            if (ctx.flagsChanged) {
                syncFlagsVarsAndPush(player, data);
            }
        }

        if (ctx.activatedCount == 0 && !shouldCompleteQuest(def, qdata)) {
            ArcQuestLog.warn(ArcQuestLog.Category.QUEST_PROGRESS, "Choice resolved but no next phase activated: quest={}, phase={}, choice={}",
                    questId, resolvedPhaseId, choiceIndex);
            return QuestRejectCodeDictionary.Code.CHOICE_TARGET_PHASE_MISSING;
        }

        return QuestRejectCodeDictionary.Code.OK;
    }
}
