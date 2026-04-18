package org.com.arc_quest.quest.logic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.quest.api.ObjectiveEntry;
import org.com.arc_quest.quest.api.PhaseDefinition;
import org.com.arc_quest.quest.api.QuestDefinition;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestRuntimeData;
import org.com.arc_quest.quest.registry.QuestRegistry;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * 声明式依赖规则。
 * 
 * <p>定义任务与对话之间的依赖关系,当某个事件发生时自动执行对应的动作。</p>
 * 
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 任务完成后解锁对话
 * DependencyRule rule = DependencyRule.builder()
 *     .whenQuestCompleted("arc_quest:main_quest_1")
 *     .thenUnlockDialogue("arc_quest:villager_talk_2")
 *     .build();
 * 
 * // 对话完成后标记任务目标
 * DependencyRule rule2 = DependencyRule.builder()
 *     .whenDialogueCompleted("arc_quest:intro_talk")
 *     .thenMarkObjectiveComplete("arc_quest:starter_quest", "talk_to_villager")
 *     .build();
 * }</pre>
 * 
 * <h2>触发类型</h2>
 * <ul>
 *   <li>{@link TriggerType#QUEST_COMPLETED} - 任务完成</li>
 *   <li>{@link TriggerType#QUEST_FAILED} - 任务失败</li>
 *   <li>{@link TriggerType#QUEST_STARTED} - 任务开始</li>
 *   <li>{@link TriggerType#DIALOGUE_COMPLETED} - 对话完成(到达终端节点)</li>
 * </ul>
 * 
 * <h2>动作类型</h2>
 * <ul>
 *   <li>{@link ActionType#UNLOCK_DIALOGUE} - 解锁对话(设置 Flag)</li>
 *   <li>{@link ActionType#MARK_OBJECTIVE_COMPLETE} - 标记任务目标完成</li>
 *   <li>{@link ActionType#START_QUEST} - 启动新任务</li>
 *   <li>{@link ActionType#SET_VARIABLE} - 设置变量</li>
 * </ul>
 */
public final class DependencyRule {
    
    private final String ruleId;
    private final TriggerType triggerType;
    private final String triggerSource;  // questId 或 dialogueId
    private final ActionType actionType;
    private final String actionTarget;   // dialogueId, objectiveId, questId 等
    @Nullable
    private final String actionParam;    // 额外参数(如 objectiveId)
    
    private DependencyRule(String ruleId, TriggerType triggerType, String triggerSource,
                          ActionType actionType, String actionTarget, @Nullable String actionParam) {
        this.ruleId = Objects.requireNonNull(ruleId);
        this.triggerType = Objects.requireNonNull(triggerType);
        this.triggerSource = Objects.requireNonNull(triggerSource);
        this.actionType = Objects.requireNonNull(actionType);
        this.actionTarget = Objects.requireNonNull(actionTarget);
        this.actionParam = actionParam;
    }
    
    /**
     * 执行规则动作。
     * 
     * @param player 玩家
     * @param capability 玩家的任务能力
     */
    public void execute(ServerPlayer player, IQuestCapability capability) {
        switch (actionType) {
            case UNLOCK_DIALOGUE -> {
                // 解锁对话 = 设置 Flag
                String flagKey = "dialogue_unlocked:" + actionTarget;
                capability.setFlag(flagKey);
            }
            case MARK_OBJECTIVE_COMPLETE -> {
                if (actionParam != null) {
                    // actionTarget = questId, actionParam = objectiveIndex
                    QuestRuntimeData questData = capability.getActiveQuest(actionTarget);
                    if (questData != null) {
                        try {
                            int objIndex = Integer.parseInt(actionParam);
                            QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(actionTarget));
                            if (def != null) {
                                PhaseDefinition currentPhase = def.getAllPhases().stream()
                                    .filter(p -> p.getPhaseId().equals(questData.getCurrentPhaseId()))
                                    .findFirst()
                                    .orElse(null);
                                
                                if (currentPhase != null && objIndex >= 0 && objIndex < currentPhase.getObjectives().size()) {
                                    int requiredCount = currentPhase.getObjectives().get(objIndex).getRequiredCount();
                                    questData.setObjectiveProgress(objIndex, requiredCount);
                                }
                            }
                        } catch (NumberFormatException e) {
                            // 忽略无效格式
                        }
                    }
                }
            }
            case START_QUEST -> {
                // 由 CrossSystemBridge 处理,这里不直接启动(需要 QuestDefinition)
            }
            case SET_VARIABLE -> {
                if (actionParam != null) {
                    try {
                        int value = Integer.parseInt(actionParam);
                        capability.setVariable(actionTarget, value);
                    } catch (NumberFormatException e) {
                        // 忽略无效值
                    }
                }
            }
        }
    }
    
    public String getRuleId() { return ruleId; }
    public TriggerType getTriggerType() { return triggerType; }
    public String getTriggerSource() { return triggerSource; }
    public ActionType getActionType() { return actionType; }
    public String getActionTarget() { return actionTarget; }
    @Nullable
    public String getActionParam() { return actionParam; }
    
    /**
     * 判断此规则是否匹配给定的触发事件。
     */
    public boolean matches(TriggerType type, String source) {
        return this.triggerType == type && this.triggerSource.equals(source);
    }
    
    /**
     * 创建构建器。
     */
    public static Builder builder() {
        return new Builder();
    }
    
    // ═══════════════════════════════════════════════
    //  枚举
    // ═══════════════════════════════════════════════
    
    public enum TriggerType {
        QUEST_COMPLETED,
        QUEST_FAILED,
        QUEST_STARTED,
        DIALOGUE_COMPLETED
    }
    
    public enum ActionType {
        UNLOCK_DIALOGUE,
        MARK_OBJECTIVE_COMPLETE,
        START_QUEST,
        SET_VARIABLE
    }
    
    // ═══════════════════════════════════════════════
    //  构建器
    // ═══════════════════════════════════════════════
    
    public static class Builder {
        private String ruleId;
        private TriggerType triggerType;
        private String triggerSource;
        private ActionType actionType;
        private String actionTarget;
        private String actionParam;
        
        public Builder id(String ruleId) {
            this.ruleId = ruleId;
            return this;
        }
        
        public Builder whenQuestCompleted(String questId) {
            this.triggerType = TriggerType.QUEST_COMPLETED;
            this.triggerSource = questId;
            return this;
        }
        
        public Builder whenQuestFailed(String questId) {
            this.triggerType = TriggerType.QUEST_FAILED;
            this.triggerSource = questId;
            return this;
        }
        
        public Builder whenQuestStarted(String questId) {
            this.triggerType = TriggerType.QUEST_STARTED;
            this.triggerSource = questId;
            return this;
        }
        
        public Builder whenDialogueCompleted(String dialogueId) {
            this.triggerType = TriggerType.DIALOGUE_COMPLETED;
            this.triggerSource = dialogueId;
            return this;
        }
        
        public Builder thenUnlockDialogue(String dialogueId) {
            this.actionType = ActionType.UNLOCK_DIALOGUE;
            this.actionTarget = dialogueId;
            return this;
        }
        
        public Builder thenMarkObjectiveComplete(String questId, int objectiveIndex) {
            this.actionType = ActionType.MARK_OBJECTIVE_COMPLETE;
            this.actionTarget = questId;
            this.actionParam = String.valueOf(objectiveIndex);
            return this;
        }
        
        public Builder thenStartQuest(String questId) {
            this.actionType = ActionType.START_QUEST;
            this.actionTarget = questId;
            return this;
        }
        
        public Builder thenSetVariable(String variableKey, int value) {
            this.actionType = ActionType.SET_VARIABLE;
            this.actionTarget = variableKey;
            this.actionParam = String.valueOf(value);
            return this;
        }
        
        public DependencyRule build() {
            if (ruleId == null) {
                ruleId = triggerSource + "_" + actionTarget;
            }
            return new DependencyRule(ruleId, triggerType, triggerSource, 
                                     actionType, actionTarget, actionParam);
        }
    }
}
