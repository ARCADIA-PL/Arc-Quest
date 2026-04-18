package org.com.arc_quest.quest.logic;

import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 跨系统桥接管理器。
 * 
 * <p>管理任务↔对话之间的依赖规则,当事件发生时自动执行对应的动作。</p>
 * 
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 注册规则
 * CrossSystemBridge bridge = CrossSystemBridge.INSTANCE;
 * bridge.registerRule(DependencyRule.builder()
 *     .whenQuestCompleted("arc_quest:main_quest_1")
 *     .thenUnlockDialogue("arc_quest:villager_talk_2")
 *     .build());
 * 
 * // 触发事件(在 QuestCapabilityImpl.markCompleted 中调用)
 * bridge.onQuestCompleted(player, capability, "arc_quest:main_quest_1");
 * 
 * // 触发对话完成事件(在 DialogueSession.end 中调用)
 * bridge.onDialogueCompleted(player, capability, "arc_quest:intro_talk");
 * }</pre>
 * 
 * <h2>线程安全</h2>
 * <p>规则列表使用 CopyOnWriteArrayList,支持并发读取和少量写入。</p>
 */
public final class CrossSystemBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrossSystemBridge.class);
    
    public static final CrossSystemBridge INSTANCE = new CrossSystemBridge();
    
    private final List<DependencyRule> rules = new CopyOnWriteArrayList<>();
    
    // 索引优化
    private final Map<String, List<DependencyRule>> questTriggerIndex = new HashMap<>();
    private final Map<String, List<DependencyRule>> dialogueTriggerIndex = new HashMap<>();
    
    private boolean frozen = false;
    
    private CrossSystemBridge() {}
    
    /**
     * 注册一条依赖规则。
     * 
     * @param rule 要注册的规则
     * @throws IllegalStateException 如果桥接器已冻结
     */
    public void registerRule(DependencyRule rule) {
        if (frozen) {
            throw new IllegalStateException("CrossSystemBridge is frozen. Cannot register more rules.");
        }
        rules.add(Objects.requireNonNull(rule));
        
        // ⭐ 建立索引
        String source = rule.getTriggerSource();
        switch (rule.getTriggerType()) {
            case QUEST_COMPLETED, QUEST_FAILED, QUEST_STARTED ->
                questTriggerIndex.computeIfAbsent(source, k -> new ArrayList<>()).add(rule);
            case DIALOGUE_COMPLETED ->
                dialogueTriggerIndex.computeIfAbsent(source, k -> new ArrayList<>()).add(rule);
        }
        
        LOGGER.debug("[CrossSystemBridge] Registered rule: {}", rule.getRuleId());
    }
    
    /**
     * 批量注册规则。
     */
    public void registerRules(Collection<DependencyRule> ruleList) {
        for (DependencyRule rule : ruleList) {
            registerRule(rule);
        }
    }
    
    /**
     * 冻结桥接器,禁止再注册新规则。
     * <p>应在模组初始化完成后调用。</p>
     */
    public void freeze() {
        frozen = true;
        LOGGER.info("[CrossSystemBridge] Frozen with {} rules.", rules.size());
    }
    
    public boolean isFrozen() {
        return frozen;
    }
    
    /**
     * 获取所有已注册的规则数量。
     */
    public int getRuleCount() {
        return rules.size();
    }
    
    /**
     * 获取所有已注册的规则(用于验证)。
     */
    public List<DependencyRule> getAllRules() {
        return Collections.unmodifiableList(rules);
    }
    
    // ═══════════════════════════════════════════════
    //  事件触发方法
    // ═══════════════════════════════════════════════
    
    /**
     * 任务完成事件。
     * 
     * @param player 玩家
     * @param capability 玩家的任务能力
     * @param questId 完成的任务 ID
     */
    public void onQuestCompleted(ServerPlayer player, IQuestCapability capability, String questId) {
        triggerRules(player, capability, DependencyRule.TriggerType.QUEST_COMPLETED, questId);
    }
    
    /**
     * 任务失败事件。
     */
    public void onQuestFailed(ServerPlayer player, IQuestCapability capability, String questId) {
        triggerRules(player, capability, DependencyRule.TriggerType.QUEST_FAILED, questId);
    }
    
    /**
     * 任务开始事件。
     */
    public void onQuestStarted(ServerPlayer player, IQuestCapability capability, String questId) {
        triggerRules(player, capability, DependencyRule.TriggerType.QUEST_STARTED, questId);
    }
    
    /**
     * 对话完成事件。
     * 
     * @param player 玩家
     * @param capability 玩家的任务能力
     * @param dialogueId 完成的对话 ID
     */
    public void onDialogueCompleted(ServerPlayer player, IQuestCapability capability, String dialogueId) {
        triggerRules(player, capability, DependencyRule.TriggerType.DIALOGUE_COMPLETED, dialogueId);
    }
    
    /**
     * 触发匹配的规则。
     */
    private void triggerRules(ServerPlayer player, IQuestCapability capability,
                             DependencyRule.TriggerType type, String source) {
        // 通过索引直接获取匹配的规则
        List<DependencyRule> matchedRules = switch (type) {
            case QUEST_COMPLETED, QUEST_FAILED, QUEST_STARTED ->
                questTriggerIndex.getOrDefault(source, Collections.emptyList());
            case DIALOGUE_COMPLETED ->
                dialogueTriggerIndex.getOrDefault(source, Collections.emptyList());
        };
        
        if (matchedRules.isEmpty()) {
            return;
        }
        
        int executedCount = 0;
        for (DependencyRule rule : matchedRules) {
            try {
                LOGGER.debug("[CrossSystemBridge] Executing rule '{}' for {} {}", 
                    rule.getRuleId(), type, source);
                rule.execute(player, capability);
                executedCount++;
            } catch (Exception e) {
                LOGGER.error("[CrossSystemBridge] Failed to execute rule '{}': {}", 
                    rule.getRuleId(), e.getMessage(), e);
            }
        }
        
        if (executedCount > 0) {
            LOGGER.info("[CrossSystemBridge] Triggered {} rules for {} {}", 
                executedCount, type, source);
        }
    }
    
    /**
     * 清除所有规则(仅用于测试)。
     */
    public void clearRulesForTesting() {
        if (frozen) {
            throw new IllegalStateException("Cannot clear rules when frozen.");
        }
        rules.clear();
    }
}
