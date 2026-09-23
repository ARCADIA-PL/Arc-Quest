package org.arcadia.arc_quest.quest.logic;

import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import org.arcadia.arc_quest.api.event.quest.QuestAcceptedEvent;
import org.arcadia.arc_quest.api.event.quest.QuestStartedEvent;
import org.arcadia.arc_quest.core.CoreProcessors;
import org.arcadia.arc_quest.core.execution.CoreRule;
import org.arcadia.arc_quest.core.execution.ExecutionObserver;
import org.arcadia.arc_quest.quest.api.IReward;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestConditionContext;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.event.QuestChangeEvent;
import org.arcadia.arc_quest.quest.event.QuestEventBus;
import org.arcadia.arc_quest.quest.logic.profile.CollectionQuestEngine;
import org.arcadia.arc_quest.quest.network.QuestRejectCodeDictionary;
import org.arcadia.arc_quest.quest.registry.QuestAcceptanceRuleRegistry;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.registry.QuestRewardPolicyRegistry;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import static org.arcadia.arc_quest.quest.logic.QuestProgressEffects.playChapterSound;

/**
 * 服务端任务推进的稳定门面。公开签名及附属 Mixin 注入点保留，内部按职责委托。
 */
public final class QuestProgressHandler {
    private static final QuestLifecycleService.RewardGrant REWARDS = QuestProgressHandler::grantRewards;
    private static final QuestLifecycleService LIFECYCLE = new QuestLifecycleService(REWARDS);
    private static final QuestPhaseProgression PHASES = new QuestPhaseProgression(LIFECYCLE, REWARDS);
    private static final QuestChoiceService CHOICES = new QuestChoiceService(PHASES, LIFECYCLE);
    private static final QuestAdministrativeProgression ADMINISTRATION =
            new QuestAdministrativeProgression(PHASES, LIFECYCLE);

    private QuestProgressHandler() {
    }

    public static boolean acceptQuest(ServerPlayer player, String questId) {
        return acceptQuestWithCode(player, questId) == QuestRejectCodeDictionary.Code.OK;
    }

    // 附属 Mixin 依赖本方法的第三个 CoreRule.require 调用，规则及顺序必须保留。
    public static QuestRejectCodeDictionary.Code acceptQuestWithCode(ServerPlayer player, String questId) {
        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) {
            ArcQuestLog.warn(ArcQuestLog.Category.QUEST_PROGRESS, "Cannot accept unknown quest: {}", questId);
            return QuestRejectCodeDictionary.Code.QUEST_NOT_FOUND;
        }

        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);

        if (data == null) return QuestRejectCodeDictionary.Code.NOT_ACTIVE;

        QuestRejectCodeDictionary.Code extensionResult = QuestAcceptanceRuleRegistry.evaluate(player, def, data);
        if (extensionResult != null) return extensionResult;

        if (def.isCollectionQuest()) {
            return CollectionQuestEngine.acceptQuest(player, data, def);
        }

        Set<ResourceLocation> completedQuests = data.getCompletedQuestLocations();
        PhaseDefinition firstPhase = def.selectInitialPhase(player.getRandom());
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
        PHASES.incrementObjective(player, questId, phaseId, objIndex, amount);
    }

    public static void incrementCollectionEntry(ServerPlayer player,
                                                String questId,
                                                String phaseId,
                                                int amount) {
        QuestCollectionProgression.incrementCollectionEntry(player, questId, phaseId, amount);
    }

    public static void revealCollectionEntry(ServerPlayer player,
                                             String questId,
                                             String phaseId) {
        QuestCollectionProgression.revealCollectionEntry(player, questId, phaseId);
    }

    public static void refreshCollectionVisibility(ServerPlayer player, String questId) {
        QuestCollectionProgression.refreshCollectionVisibility(player, questId);
    }

    public static void discoverCollectionEntry(ServerPlayer player,
                                               String questId,
                                               String phaseId) {
        QuestCollectionProgression.discoverCollectionEntry(player, questId, phaseId);
    }

    public static void addCollectionUniqueKey(ServerPlayer player,
                                              String questId,
                                              String phaseId,
                                              String uniqueKey) {
        QuestCollectionProgression.addCollectionUniqueKey(player, questId, phaseId, uniqueKey);
    }

    public static void advanceToPhase(ServerPlayer player,
                                      ArcQuestPlayer data,
                                      QuestRuntimeData qdata,
                                      QuestDefinition def,
                                      String nextPhaseId) {
        PHASES.advanceToPhase(player, data, qdata, def, nextPhaseId);
    }

    public static QuestRejectCodeDictionary.Code confirmManualPhaseAdvance(ServerPlayer player,
                                                                           String questId,
                                                                           String phaseId) {
        return PHASES.confirmManualPhaseAdvance(player, questId, phaseId);
    }

    public static boolean handlePlayerChoice(ServerPlayer player,
                                             String questId,
                                             String phaseId,
                                             int choiceIndex) {
        return handlePlayerChoiceWithCode(player, questId, phaseId, choiceIndex) == QuestRejectCodeDictionary.Code.OK;
    }

    public static boolean handlePlayerChoice(ServerPlayer player,
                                             String questId,
                                             int choiceIndex) {
        return handlePlayerChoiceWithCode(player, questId, "", choiceIndex) == QuestRejectCodeDictionary.Code.OK;
    }

    public static QuestRejectCodeDictionary.Code handlePlayerChoiceWithCode(ServerPlayer player,
                                                                            String questId,
                                                                            String phaseId,
                                                                            int choiceIndex) {
        return CHOICES.handlePlayerChoiceWithCode(player, questId, phaseId, choiceIndex);
    }

    public static void failQuest(ServerPlayer player, String questId) {
        LIFECYCLE.failQuest(player, questId);
    }

    public static boolean abandonQuest(ServerPlayer player, String questId) {
        return abandonQuestWithCode(player, questId) == QuestRejectCodeDictionary.Code.OK;
    }

    public static QuestRejectCodeDictionary.Code abandonQuestWithCode(ServerPlayer player, String questId) {
        return LIFECYCLE.abandonQuestWithCode(player, questId);
    }

    public static QuestRejectCodeDictionary.Code abandonPhase(
            ServerPlayer player, String questId, String phaseId) {
        return PHASES.abandonPhase(player, questId, phaseId);
    }

    public static void forceComplete(ServerPlayer player, String questId) {
        forceCompleteResult(player, questId);
    }

    public static QuestRejectCodeDictionary.Code forceCompleteResult(ServerPlayer player, String questId) {
        return ADMINISTRATION.forceCompleteResult(player, questId);
    }

    public static void forceCompletePhase(ServerPlayer player, String questId, String phaseId) {
        forceCompletePhaseResult(player, questId, phaseId);
    }

    public static QuestRejectCodeDictionary.Code forceCompletePhaseResult(ServerPlayer player, String questId, String phaseId) {
        return ADMINISTRATION.forceCompletePhaseResult(player, questId, phaseId);
    }

    public static void syncToClient(ServerPlayer player, String questId) {
        QuestProgressEffects.syncToClient(player, questId);
    }

    public static void rebuildTrackingIndex(ServerPlayer player, ArcQuestPlayer data) {
        QuestObjectiveService.rebuildTrackingIndex(player, data);
    }

    public static void registerPhaseObjectives(ServerPlayer player,
                                               QuestDefinition def,
                                               PhaseDefinition phase) {
        QuestObjectiveService.registerPhaseObjectives(player, def, phase);
    }

    public static int resolveRequiredCount(ServerPlayer player, ObjectiveEntry obj, ArcQuestPlayer data) {
        return QuestObjectiveService.resolveRequiredCount(player, obj, data);
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
        return QuestObjectiveService.computeRequiredCount(modeRaw, normalizedMode, fallbackRequired, base, min, max, playerLevel, countPerLevel, variableValue, countPerVar, objectiveDebugId);
    }

    public static List<ResourceLocation> objectiveKeyTargets(ObjectiveEntry obj) {
        return QuestObjectiveService.objectiveKeyTargets(obj);
    }

    // 附属 Mixin 重定向此处的 IReward.grant；所有服务通过 REWARDS 回到该兼容点。
    private static void grantRewards(ServerPlayer player, List<IReward> rewards, String context) {
        for (IReward reward : rewards) {
            try {
                if (QuestRewardPolicyRegistry.evaluate(player, reward, context)
                        == QuestRewardPolicyRegistry.Decision.SKIP) {
                    ArcQuestLog.debug(ArcQuestLog.Category.QUEST_PROGRESS, "Skipped {} reward for {}: {}",
                            context, player.getGameProfile().getName(), reward.describe());
                    continue;
                }
                ArcQuestLog.info(ArcQuestLog.Category.QUEST_PROGRESS, "Granting {} reward to {}: {}", context, player.getGameProfile().getName(), reward.describe());
                reward.grant(player);
            } catch (Exception e) {
                ArcQuestLog.error(ArcQuestLog.Category.QUEST_PROGRESS, "Error granting {} reward: {}", context, e.getMessage(), e);
            }
        }
    }

    private static void publishQuestAccepted(QuestAcceptanceContext context) {
        ResourceLocation questId = ResourceLocation.parse(context.questId());
        QuestEventBus.fire(QuestChangeEvent.questAccepted(questId));
        MinecraftForge.EVENT_BUS.post(new QuestAcceptedEvent(context.player(), questId));
        MinecraftForge.EVENT_BUS.post(new QuestStartedEvent(context.player(), questId));
        playChapterSound(context.player(), context.definition().getChapterStartSound());
    }

    private static QuestRejectCodeDictionary.Code applyQuestAcceptance(QuestAcceptanceContext context) {
        return PHASES.applyQuestAcceptance(context.player(), context.data(), context.definition(),
                context.questId(), context.initialPhase());
    }

    private record QuestAcceptanceContext(ServerPlayer player, ArcQuestPlayer data,
                                          QuestDefinition definition, String questId,
                                          PhaseDefinition initialPhase,
                                          QuestConditionContext conditionContext) {
    }
}
