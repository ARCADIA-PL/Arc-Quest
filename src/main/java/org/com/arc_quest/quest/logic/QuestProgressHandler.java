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
import org.com.arc_quest.quest.network.QuestSyncCoordinator;
import org.com.arc_quest.quest.network.QuestRejectCodeDictionary;
import org.com.arc_quest.quest.registry.QuestRegistry;
import org.com.arc_quest.quest.tracking.ObjectiveTracker;
import org.com.arc_quest.quest.tracking.TrackedObjective;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 任务进度推进的核心逻辑引擎。
 * <p>
 * 所有方法仅在服务端调用。每个方法完成后自动触发：
 * <ol>
 *   <li>Capability 数据更新</li>
 *   <li>ObjectiveTracker 索引更新</li>
 *   <li>网络同步到客户端</li>
 *   <li>QuestEventBus 事件分发</li>
 * </ol>
 */
public final class QuestProgressHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private QuestProgressHandler() {
    }

    // ═══════════════════════════════════════════════════════
    //  接受任务
    // ═══════════════════════════════════════════════════════

    /**
     * 玩家接受任务。
     *
     * @return true 如果成功接受
     */
    public static boolean acceptQuest(ServerPlayer player, String questId) {
        return acceptQuestWithCode(player, questId) == QuestRejectCodeDictionary.Code.OK;
    }

    public static QuestRejectCodeDictionary.Code acceptQuestWithCode(ServerPlayer player, String questId) {
        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) {
            LOGGER.warn("[ArcQuest] Cannot accept unknown quest: {}", questId);
            return QuestRejectCodeDictionary.Code.QUEST_NOT_FOUND;
        }

        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);

        // 检查：是否已经在进行或已完成
        if (cap.isQuestActive(questId)) {
            LOGGER.debug("[ArcQuest] Quest already active: {}", questId);
            return QuestRejectCodeDictionary.Code.ALREADY_ACTIVE;
        }
        if (cap.isQuestCompleted(questId) && !def.isRepeatable()) {
            LOGGER.debug("[ArcQuest] Quest already completed and not repeatable: {}", questId);
            return QuestRejectCodeDictionary.Code.ALREADY_COMPLETED_NOT_REPEATABLE;
        }

        // 检查前置条件
        Set<ResourceLocation> completedQuests = cap.getCompletedQuestLocations();
        for (ICondition cond : def.getUnlockConditions()) {
            if (!cond.test(player, completedQuests, cap.getAllFlags(), cap.getAllVariables())) {
                LOGGER.debug("[ArcQuest] Accept condition not met for quest: {}", questId);
                return QuestRejectCodeDictionary.Code.UNLOCK_CONDITION_NOT_MET;
            }
        }

        // 确定起始阶段
        PhaseDefinition firstPhase = def.getInitialPhase();
        if (firstPhase == null) {
            LOGGER.warn("[ArcQuest] Quest has no phases: {}", questId);
            return QuestRejectCodeDictionary.Code.NO_INITIAL_PHASE;
        }

        // 创建运行时数据
        QuestRuntimeData data = new QuestRuntimeData(
                questId,
                firstPhase.getPhaseId(),
                firstPhase.getObjectives().size(),
                player.getServer() != null ? player.getServer().getTickCount() : 0
        );
        cap.addActiveQuest(data);

        // 设置任务接受时的 flag
        for (String flag : def.getFlagsToSetOnAccept()) {
            cap.setFlag(flag);
        }

        // 注册到追踪器
        registerPhaseObjectives(player, def, firstPhase);

        // 同步 & 事件
        syncQuestStateAndPush(player, data);
        syncFlagsVarsAndPush(player, cap);
        QuestEventBus.fire(QuestChangeEvent.questAccepted(ResourceLocation.parse(questId)));

        // 发布 Forge 事件（供附属模组监听）
        MinecraftForge.EVENT_BUS.post(new QuestAcceptedEvent(player, ResourceLocation.parse(questId)));
        MinecraftForge.EVENT_BUS.post(new QuestStartedEvent(player, ResourceLocation.parse(questId)));

        LOGGER.info("[ArcQuest] Player {} accepted quest: {}",
                player.getGameProfile().getName(), questId);
        return QuestRejectCodeDictionary.Code.OK;
    }

    // ═══════════════════════════════════════════════════════
    //  目标进度推进（最核心的方法）
    // ═══════════════════════════════════════════════════════

    /**
     * 推进指定任务目标的进度。
     * <p>
     * 由 {@link org.com.arc_quest.quest.tracking.QuestEventManager} 调用。
     *
     * @param player   目标玩家
     * @param questId  任务 ID
     * @param phaseId  阶段 ID（用于验证）
     * @param objIndex 目标索引
     * @param amount   增加的数量
     */
    public static void incrementObjective(ServerPlayer player,
                                          String questId,
                                          String phaseId,
                                          int objIndex,
                                          int amount) {
        if (amount <= 0) return;

        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);

        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data == null || data.getState() != QuestState.ACTIVE) return;

        // 验证阶段一致性
        if (!data.getCurrentPhaseId().equals(phaseId)) return;

        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) return;

        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase == null) return;

        if (objIndex < 0 || objIndex >= phase.getObjectives().size()) return;

        ObjectiveEntry objEntry = phase.getObjectives().get(objIndex);
        int required = objEntry.getRequiredCount();

        // 检查是否已完成
        int currentProgress = data.getObjectiveProgress(objIndex);
        if (currentProgress >= required) return; // 已达标，忽略

        // 推进
        int newProgress = data.incrementProgress(objIndex, amount, required);

        LOGGER.debug("[ArcQuest] Objective progress: {}/{}/{} #{} = {}/{}",
                player.getGameProfile().getName(), questId, phaseId,
                objIndex, newProgress, required);

        // 使用增量同步替代全量同步，减少带宽占用
        syncDeltaProgressAndPush(player, questId, objIndex, newProgress);

        // 触发事件
        QuestEventBus.fire(QuestChangeEvent.objectiveProgressed(
                ResourceLocation.parse(questId), objIndex, newProgress, required));
        
        // 发布 Forge 事件（供附属模组监听）
        MinecraftForge.EVENT_BUS.post(new QuestProgressChangedEvent(
                player, ResourceLocation.parse(questId), phaseId,
                objIndex, currentProgress, newProgress, required));

        // 检查阶段是否完成
        checkPhaseCompletion(player, cap, data, def, phase);
    }

    // ═══════════════════════════════════════════════════════
    //  阶段完成检查
    // ═══════════════════════════════════════════════════════

    private static void checkPhaseCompletion(ServerPlayer player,
                                             IQuestCapability cap,
                                             QuestRuntimeData data,
                                             QuestDefinition def,
                                             PhaseDefinition phase) {
        // 检查所有目标是否达标
        List<ObjectiveEntry> objectives = phase.getObjectives();
        for (int i = 0; i < objectives.size(); i++) {
            if (data.getObjectiveProgress(i) < objectives.get(i).getRequiredCount()) {
                return; // 至少有一个未完成
            }
        }

        LOGGER.info("[ArcQuest] Phase completed: {}/{} for player {}",
                data.getQuestId(), phase.getPhaseId(),
                player.getGameProfile().getName());

        // 发布 Forge 事件（供附属模组监听）
        MinecraftForge.EVENT_BUS.post(new QuestPhaseCompletedEvent(
                player, ResourceLocation.parse(data.getQuestId()), phase.getPhaseId()));

        // 发放阶段完成奖励
        grantRewards(player, phase.getPhaseRewards(), "phase");

        // 从追踪器中移除旧阶段目标
        unregisterPhaseObjectives(player, def, phase);

        // 确定下一步：看 transitions 或 choices
        List<PhaseTransition> transitions = phase.getTransitions();
        boolean hasChoices = phase.hasChoices();

        if (hasChoices) {
            // 有 choices → 等待玩家选择（不自动推进）
            LOGGER.debug("[ArcQuest] Phase {} has choices, awaiting player selection.",
                    phase.getPhaseId());

            // 触发事件通知 GUI 显示选择界面
            QuestEventBus.fire(QuestChangeEvent.phaseChanged(
                    ResourceLocation.parse(data.getQuestId()),
                    data.getCurrentPhaseId(),
                    data.getCurrentPhaseId()));

            // 同步状态并显示Toast
            syncQuestStateAndPush(player, data);
            return;
        }

        if (transitions.isEmpty()) {
            completeQuest(player, cap, data, def);
            return;
        }

        if (transitions.size() == 1 && !transitions.get(0).requiresChoice()) {
            PhaseTransition auto = transitions.get(0);

            Set<ResourceLocation> completedQuests = cap.getCompletedQuestLocations();
            boolean conditionsMet = auto.getCondition() == null ||
                    auto.getCondition().test(player, completedQuests, cap.getAllFlags(), cap.getAllVariables());

            if (conditionsMet) {
                advanceToPhase(player, cap, data, def, auto.getTargetPhaseId());
            } else {
                LOGGER.debug("[ArcQuest] Auto-transition conditions not met, quest stalls: {}/{}",
                        data.getQuestId(), auto.getTargetPhaseId());
            }
            return;
        }

        // 多个过渡，等待玩家选择
        LOGGER.debug("[ArcQuest] Phase {} has {} transitions, awaiting player choice.",
                phase.getPhaseId(), transitions.size());

        QuestEventBus.fire(QuestChangeEvent.phaseChanged(
                ResourceLocation.parse(data.getQuestId()),
                data.getCurrentPhaseId(),
                data.getCurrentPhaseId()));

        syncQuestStateAndPush(player, data);
    }

    // ═══════════════════════════════════════════════════════
    // 阶段推进
    // ═══════════════════════════════════════════════════════

    /**
     * 推进到指定阶段。
     */
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

        // 保存旧阶段 ID，在更新前记录，供事件使用
        String fromPhaseId = data.getCurrentPhaseId();

        // 设置新阶段
        data.setCurrentPhaseId(nextPhaseId);
        data.resetObjectives(nextPhase.getObjectives().size());

        // 注册新阶段目标到追踪器
        registerPhaseObjectives(player, def, nextPhase);

        LOGGER.info("[ArcQuest] Player {} advanced to phase: {}/{}",
                player.getGameProfile().getName(), def.getId(), nextPhaseId);

        // 同步 & 事件
        syncQuestStateAndPush(player, data);
        QuestEventBus.fire(QuestChangeEvent.phaseChanged(
                def.getId(), fromPhaseId, nextPhaseId));
        
        // 发布 Forge 事件（供附属模组监听）
        MinecraftForge.EVENT_BUS.post(new QuestPhaseChangedEvent(
                player, def.getId(), fromPhaseId, nextPhaseId));
    }

    /**
     * 玩家选择分支过渡（由网络包 C2S 触发）。
     *
     * @param choiceIndex 玩家选择的 choice 索引
     * @return true 选择并推进成功；false 被拒绝或无效
     */
    public static boolean handlePlayerChoice(ServerPlayer player,
                                             String questId,
                                             int choiceIndex) {
        return handlePlayerChoiceWithCode(player, questId, choiceIndex) == QuestRejectCodeDictionary.Code.OK;
    }

    public static QuestRejectCodeDictionary.Code handlePlayerChoiceWithCode(ServerPlayer player,
                                                                             String questId,
                                                                             int choiceIndex) {
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);

        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data == null) return QuestRejectCodeDictionary.Code.NOT_ACTIVE;

        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) return QuestRejectCodeDictionary.Code.QUEST_NOT_FOUND;

        PhaseDefinition currentPhase = def.getPhase(data.getCurrentPhaseId());
        if (currentPhase == null) return QuestRejectCodeDictionary.Code.PHASE_NOT_FOUND;

        // 从 choices 列表中获取（而不是 transitions）
        List<ChoiceOption> choices = currentPhase.getChoices();
        if (choiceIndex < 0 || choiceIndex >= choices.size()) {
            LOGGER.warn("[ArcQuest] Invalid choice index {} for quest {}", choiceIndex, questId);
            return QuestRejectCodeDictionary.Code.INVALID_CHOICE_INDEX;
        }

        ChoiceOption chosen = choices.get(choiceIndex);

        // 验证可见性条件
        Set<ResourceLocation> completedQuests = cap.getCompletedQuestLocations();
        ICondition visibleCondition = chosen.getVisibleCondition();
        boolean conditionsMet = visibleCondition == null ||
                visibleCondition.test(player, completedQuests, cap.getAllFlags(), cap.getAllVariables());
        if (!conditionsMet) {
            LOGGER.debug("[ArcQuest] Choice conditions not met for index {}", choiceIndex);
            return QuestRejectCodeDictionary.Code.CHOICE_CONDITION_NOT_MET;
        }

        // 设置标记
        String flagToSet = chosen.getFlagToSet();
        if (flagToSet != null && !flagToSet.isEmpty()) {
            cap.setFlag(flagToSet);
            LOGGER.debug("[ArcQuest] Set flag '{}' from choice", flagToSet);
        }

        // 推进到目标 phase
        String targetPhaseId = chosen.getTargetPhaseId();
        if (targetPhaseId != null && !targetPhaseId.isEmpty()) {
            advanceToPhase(player, cap, data, def, targetPhaseId);
            return QuestRejectCodeDictionary.Code.OK;
        }

        LOGGER.warn("[ArcQuest] Choice has no target phase: {}", choiceIndex);
        return QuestRejectCodeDictionary.Code.CHOICE_TARGET_PHASE_MISSING;
    }

    // ═══════════════════════════════════════════════════════
    //  任务完成 / 失败 / 放弃
    // ═══════════════════════════════════════════════════════

    private static void completeQuest(ServerPlayer player,
                                      IQuestCapability cap,
                                      QuestRuntimeData data,
                                      QuestDefinition def) {
        doCompleteQuest(player, cap, data, def, "completed");
    }

    /**
     * 使任务失败。
     */
    public static void failQuest(ServerPlayer player, String questId) {
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);

        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data == null) return;

        data.setState(QuestState.FAILED);
        cap.markFailed(questId);
        ObjectiveTracker.INSTANCE.unregisterQuest(player.getUUID(), questId);

        LOGGER.info("[ArcQuest] Player {} failed quest: {}",
                player.getGameProfile().getName(), questId);

        syncQuestStateAndPush(player, data);
        QuestEventBus.fire(QuestChangeEvent.questFailed(ResourceLocation.parse(questId)));
        
        // 发布 Forge 事件（供附属模组监听）
        MinecraftForge.EVENT_BUS.post(new QuestFailedEvent(player, ResourceLocation.parse(questId)));
    }

    /**
     * 放弃任务。
     */
    public static boolean abandonQuest(ServerPlayer player, String questId) {
        return abandonQuestWithCode(player, questId) == QuestRejectCodeDictionary.Code.OK;
    }

    public static QuestRejectCodeDictionary.Code abandonQuestWithCode(ServerPlayer player, String questId) {
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);

        if (!cap.isQuestActive(questId)) return QuestRejectCodeDictionary.Code.NOT_ACTIVE;

        // 先标记为 FAILED，再移除（这样会出现在 FAILED 标签页）
        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data != null) {
            data.setState(QuestState.FAILED);
        }
        cap.markFailed(questId);
        cap.removeActiveQuest(questId);
        ObjectiveTracker.INSTANCE.unregisterQuest(player.getUUID(), questId);

        LOGGER.info("[ArcQuest] Player {} abandoned quest: {}",
                player.getGameProfile().getName(), questId);

        syncFullDataAndPush(player, cap); // 全量同步最安全
        QuestEventBus.fire(QuestChangeEvent.questFailed(ResourceLocation.parse(questId)));
        return QuestRejectCodeDictionary.Code.OK;
    }

    /**
     * 强制完成任务（用于指令）。
     */
    public static void forceComplete(ServerPlayer player, String questId) {
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);

        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data == null) return;

        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) return;

        doCompleteQuest(player, cap, data, def, "force-completed");
    }

    /**
     * 执行任务完成的公共逻辑。
     *
     * @param player    目标玩家
     * @param cap       玩家能力数据
     * @param data      任务运行时数据
     * @param def       任务定义
     * @param logPrefix 日志前缀（"completed" 或 "force-completed"）
     */
    private static void doCompleteQuest(ServerPlayer player,
                                        IQuestCapability cap,
                                        QuestRuntimeData data,
                                        QuestDefinition def,
                                        String logPrefix) {
        String questId = data.getQuestId();

        // 发放最终奖励
        grantRewards(player, def.getCompletionRewards(), "completion");

        // 设置完成时 Flag
        def.getFlagsToSetOnComplete().forEach(cap::setFlag);

        // 标记为已完成
        data.setState(QuestState.COMPLETED);
        cap.markCompleted(questId);

        // 清理追踪
        ObjectiveTracker.INSTANCE.unregisterQuest(player.getUUID(), questId);

        LOGGER.info("[ArcQuest] Player {} {} quest: {}",
                player.getGameProfile().getName(), logPrefix, questId);

        // 同步 & 事件
        syncQuestStateAndPush(player, data);
        syncFlagsVarsAndPush(player, cap);
        QuestEventBus.fire(QuestChangeEvent.questCompleted(ResourceLocation.parse(questId)));
        
        // 发布 Forge 事件（供附属模组监听）
        MinecraftForge.EVENT_BUS.post(new QuestCompletedEvent(player, ResourceLocation.parse(questId)));
    }

    /**
     * 同步指定任务到客户端（用于指令）。
     */
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

    private static void syncDeltaProgressAndPush(ServerPlayer player, String questId, int objIndex, int newProgress) {
        QuestSyncCoordinator.syncDeltaProgressAndPush(player, questId, objIndex, newProgress);
    }

    // ═══════════════════════════════════════════════════════
    //  追踪索引管理
    // ═══════════════════════════════════════════════════════

    /**
     * 登录/重生时重建整个玩家的追踪索引。
     */
    public static void rebuildTrackingIndex(ServerPlayer player, IQuestCapability cap) {
        // 先清除旧数据
        ObjectiveTracker.INSTANCE.unregisterPlayer(player.getUUID());

        for (Map.Entry<String, QuestRuntimeData> entry : cap.getAllActiveQuests().entrySet()) {
            QuestRuntimeData data = entry.getValue();
            if (data.getState() != QuestState.ACTIVE) continue;

            QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(data.getQuestId()));
            if (def == null) continue;

            PhaseDefinition phase = def.getPhase(data.getCurrentPhaseId());
            if (phase == null) continue;

            registerPhaseObjectives(player, def, phase);
        }

        LOGGER.debug("[ArcQuest] Tracking index rebuilt for player: {} ({} active quests)",
                player.getGameProfile().getName(), cap.getAllActiveQuests().size());
    }

    /**
     * 将某阶段的所有目标注册到追踪器。
     */
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

    /**
     * 从追踪器中移除某阶段的所有目标。
     */
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

    // ═══════════════════════════════════════════════════════
    //  工具方法
    // ═══════════════════════════════════════════════════════

    /**
     * 安全发放奖励列表，单条失败不中断整体发放。
     *
     * @param player  目标玩家
     * @param rewards 奖励列表
     * @param context 日志上下文描述（如 "phase"、"completion"）
     */
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