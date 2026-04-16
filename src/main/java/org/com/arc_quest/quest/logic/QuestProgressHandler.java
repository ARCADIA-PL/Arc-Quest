package org.com.arc_quest.quest.logic;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.quest.api.*;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.com.arc_quest.quest.capability.QuestRuntimeData;
import org.com.arc_quest.quest.event.QuestChangeEvent;
import org.com.arc_quest.quest.event.QuestEventBus;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.quest.registry.QuestRegistry;
import org.com.arc_quest.quest.tracking.ObjectiveTracker;
import org.com.arc_quest.quest.tracking.TrackedObjective;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    private QuestProgressHandler() {}

    // ═══════════════════════════════════════════════════════
    //  接受任务
    // ═══════════════════════════════════════════════════════

    /**
     * 玩家接受任务。
     *
     * @return true 如果成功接受
     */
    public static boolean acceptQuest(ServerPlayer player, String questId) {
        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) {
            LOGGER.warn("[ArcQuest] Cannot accept unknown quest: {}", questId);
            return false;
        }

        IQuestCapability cap = player.getCapability(QuestCapabilityProvider.QUEST_CAP)
                .orElse(null);
        if (cap == null) return false;

        // 检查：是否已经在进行或已完成
        if (cap.isQuestActive(questId)) {
            LOGGER.debug("[ArcQuest] Quest already active: {}", questId);
            return false;
        }
        if (cap.isQuestCompleted(questId) && !def.isRepeatable()) {
            LOGGER.debug("[ArcQuest] Quest already completed and not repeatable: {}", questId);
            return false;
        }

        // 检查前置条件
        Set<ResourceLocation> completedQuests = cap.getCompletedQuests().stream()
                .map(ResourceLocation::parse)
                .collect(Collectors.toSet());
        for (ICondition cond : def.getUnlockConditions()) {
            if (!cond.test(completedQuests, cap.getAllFlags(), cap.getAllVariables())) {
                LOGGER.debug("[ArcQuest] Accept condition not met for quest: {}", questId);
                return false;
            }
        }

        // 确定起始阶段
        PhaseDefinition firstPhase = def.getInitialPhase();
        if (firstPhase == null) {
            LOGGER.warn("[ArcQuest] Quest has no phases: {}", questId);
            return false;
        }

        // 创建运行时数据
        QuestRuntimeData data = new QuestRuntimeData(
                questId,
                firstPhase.getPhaseId(),
                firstPhase.getObjectives().size(),
                player.getServer() != null ? player.getServer().getTickCount() : 0
        );
        cap.addActiveQuest(data);

        // 注册到追踪器
        registerPhaseObjectives(player, def, firstPhase);

        // 同步 & 事件
        ArcQuestNetwork.syncQuestState(player, data);
        QuestEventBus.fire(QuestChangeEvent.questAccepted(ResourceLocation.parse(questId)));

        LOGGER.info("[ArcQuest] Player {} accepted quest: {}",
                player.getGameProfile().getName(), questId);
        return true;
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

        IQuestCapability cap = player.getCapability(QuestCapabilityProvider.QUEST_CAP)
                .orElse(null);
        if (cap == null) return;

        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data == null || data.getState() != QuestState.ACTIVE) return;

        // 验证阶段一致性
        if (!data.getCurrentPhaseId().equals(phaseId)) return;

        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) return;

        PhaseDefinition phase = findPhase(def, phaseId);
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

        // 同步单目标进度
        ArcQuestNetwork.syncObjectiveProgress(player, questId, objIndex, newProgress);

        // 触发事件
        QuestEventBus.fire(QuestChangeEvent.objectiveProgressed(
                ResourceLocation.parse(questId), objIndex, newProgress, required));

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

        // 发放阶段完成奖励
        for (IReward reward : phase.getPhaseRewards()) {
            try {
                reward.grant(player);
            } catch (Exception e) {
                LOGGER.error("[ArcQuest] Error granting phase reward: {}", e.getMessage(), e);
            }
        }

        // 从追踪器中移除旧阶段目标
        unregisterPhaseObjectives(player, def, phase);

        // 确定下一步：看 transitions
        List<PhaseTransition> transitions = phase.getTransitions();

        if (transitions.isEmpty()) {
            // 没有后续阶段 → 任务完成
            completeQuest(player, cap, data, def);
            return;
        }

        if (transitions.size() == 1 && !transitions.get(0).requiresChoice()) {
            // 唯一的自动过渡
            PhaseTransition auto = transitions.get(0);

            // 检查过渡条件
            Set<ResourceLocation> completedQuests = cap.getCompletedQuests().stream()
                    .map(ResourceLocation::parse)
                    .collect(Collectors.toSet());
            boolean conditionsMet = auto.getCondition() == null || 
                    auto.getCondition().test(completedQuests, cap.getAllFlags(), cap.getAllVariables());

            if (conditionsMet) {
                advanceToPhase(player, cap, data, def, auto.getTargetPhaseId());
            } else {
                LOGGER.debug("[ArcQuest] Auto-transition conditions not met, quest stalls: {}/{}",
                        data.getQuestId(), auto.getTargetPhaseId());
            }
            return;
        }

        // 多个过渡 → 等待玩家选择
        LOGGER.debug("[ArcQuest] Phase {} has {} transitions, awaiting player choice.",
                phase.getPhaseId(), transitions.size());

        // 触发事件通知 GUI 显示选择界面
        QuestEventBus.fire(QuestChangeEvent.phaseChanged(
                ResourceLocation.parse(data.getQuestId()), 
                data.getCurrentPhaseId(), 
                data.getCurrentPhaseId()));

        // 同步状态让客户端知道需要选择
        ArcQuestNetwork.syncQuestState(player, data);
    }

    // ═══════════════════════════════════════════════════════
    //  阶段推进
    // ═══════════════════════════════════════════════════════

    /**
     * 推进到指定阶段。
     */
    public static void advanceToPhase(ServerPlayer player,
                                      IQuestCapability cap,
                                      QuestRuntimeData data,
                                      QuestDefinition def,
                                      String nextPhaseId) {
        PhaseDefinition nextPhase = findPhase(def, nextPhaseId);
        if (nextPhase == null) {
            LOGGER.error("[ArcQuest] Target phase not found: {}/{}", def.getId(), nextPhaseId);
            return;
        }

        // 更新运行时数据
        data.setCurrentPhaseId(nextPhaseId);
        data.resetObjectives(nextPhase.getObjectives().size());

        // 注册新阶段目标到追踪器
        registerPhaseObjectives(player, def, nextPhase);

        LOGGER.info("[ArcQuest] Player {} advanced to phase: {}/{}",
                player.getGameProfile().getName(), def.getId(), nextPhaseId);

        // 同步 & 事件
        ArcQuestNetwork.syncQuestState(player, data);
        QuestEventBus.fire(QuestChangeEvent.phaseChanged(
                def.getId(), data.getCurrentPhaseId(), nextPhaseId));
    }

    /**
     * 玩家选择分支过渡（由网络包 C2S 触发）。
     *
     * @param transitionIndex 玩家选择的过渡索引
     */
    public static void handlePlayerChoice(ServerPlayer player,
                                          String questId,
                                          int transitionIndex) {
        IQuestCapability cap = player.getCapability(QuestCapabilityProvider.QUEST_CAP)
                .orElse(null);
        if (cap == null) return;

        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data == null) return;

        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) return;

        PhaseDefinition currentPhase = findPhase(def, data.getCurrentPhaseId());
        if (currentPhase == null) return;

        List<PhaseTransition> transitions = currentPhase.getTransitions();
        if (transitionIndex < 0 || transitionIndex >= transitions.size()) {
            LOGGER.warn("[ArcQuest] Invalid transition index {} for quest {}", transitionIndex, questId);
            return;
        }

        PhaseTransition chosen = transitions.get(transitionIndex);

        // 验证条件
        Set<ResourceLocation> completedQuests = cap.getCompletedQuests().stream()
                .map(ResourceLocation::parse)
                .collect(Collectors.toSet());
        ICondition condition = chosen.getCondition();
        boolean conditionsMet = condition == null || 
                condition.test(completedQuests, cap.getAllFlags(), cap.getAllVariables());
        if (!conditionsMet) {
            LOGGER.debug("[ArcQuest] Transition conditions not met for choice {}", transitionIndex);
            return;
        }

        advanceToPhase(player, cap, data, def, chosen.getTargetPhaseId());
    }

    // ═══════════════════════════════════════════════════════
    //  任务完成 / 失败 / 放弃
    // ═══════════════════════════════════════════════════════

    private static void completeQuest(ServerPlayer player,
                                      IQuestCapability cap,
                                      QuestRuntimeData data,
                                      QuestDefinition def) {
        String questId = data.getQuestId();

        // 发放最终奖励
        for (IReward reward : def.getCompletionRewards()) {
            try {
                reward.grant(player);
            } catch (Exception e) {
                LOGGER.error("[ArcQuest] Error granting completion reward: {}", e.getMessage(), e);
            }
        }

        // 更新状态
        data.setState(QuestState.COMPLETED);
        cap.markCompleted(questId);

        // 清理追踪
        ObjectiveTracker.INSTANCE.unregisterQuest(player.getUUID(), questId);

        LOGGER.info("[ArcQuest] Player {} completed quest: {}",
                player.getGameProfile().getName(), questId);

        // 同步 & 事件
        ArcQuestNetwork.syncQuestState(player, data);
        ArcQuestNetwork.syncFlagsAndVars(player, cap);
        QuestEventBus.fire(QuestChangeEvent.questCompleted(ResourceLocation.parse(questId)));
    }

    /**
     * 使任务失败。
     */
    public static void failQuest(ServerPlayer player, String questId) {
        IQuestCapability cap = player.getCapability(QuestCapabilityProvider.QUEST_CAP)
                .orElse(null);
        if (cap == null) return;

        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data == null) return;

        data.setState(QuestState.FAILED);
        cap.markFailed(questId);
        ObjectiveTracker.INSTANCE.unregisterQuest(player.getUUID(), questId);

        LOGGER.info("[ArcQuest] Player {} failed quest: {}",
                player.getGameProfile().getName(), questId);

        ArcQuestNetwork.syncQuestState(player, data);
        QuestEventBus.fire(QuestChangeEvent.questFailed(ResourceLocation.parse(questId)));
    }

    /**
     * 放弃任务。
     */
    public static void abandonQuest(ServerPlayer player, String questId) {
        IQuestCapability cap = player.getCapability(QuestCapabilityProvider.QUEST_CAP)
                .orElse(null);
        if (cap == null) return;

        if (!cap.isQuestActive(questId)) return;

        cap.removeActiveQuest(questId);
        ObjectiveTracker.INSTANCE.unregisterQuest(player.getUUID(), questId);

        LOGGER.info("[ArcQuest] Player {} abandoned quest: {}",
                player.getGameProfile().getName(), questId);

        ArcQuestNetwork.syncFullData(player, cap); // 全量同步最安全
        QuestEventBus.fire(QuestChangeEvent.questFailed(ResourceLocation.parse(questId)));
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

            PhaseDefinition phase = findPhase(def, data.getCurrentPhaseId());
            if (phase == null) continue;

            registerPhaseObjectives(player, def, phase);
        }

        LOGGER.debug("[ArcQuest] Tracking index rebuilt for player: {} ({} active quests)",
                player.getGameProfile().getName(), cap.getAllActiveQuests().size());
    }

    /**
     * 将某阶段的所有目标注册到追踪器。
     */
    private static void registerPhaseObjectives(ServerPlayer player,
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

    private static PhaseDefinition findPhase(QuestDefinition def, String phaseId) {
        return def.getAllPhases().stream()
                .filter(p -> p.getPhaseId().equals(phaseId))
                .findFirst()
                .orElse(null);
    }
}