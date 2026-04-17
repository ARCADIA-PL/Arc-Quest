package org.com.arc_quest.dialogue.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.quest.api.QuestState;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.slf4j.Logger;

import java.util.Map;

/**
 * 条件文本评估器 - 在服务端评估条件并选择合适的文本。
 */
public final class ConditionalTextEvaluator {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ConditionalTextEvaluator() {
    }

    /**
     * 根据玩家状态评估条件文本，返回第一个匹配的文本。
     *
     * @param player           玩家
     * @param conditionalTexts 条件文本映射（条件标识 → 文本）
     * @param defaultText      默认文本
     * @return 匹配的文本，或默认文本
     */
    public static String evaluate(ServerPlayer player, Map<String, String> conditionalTexts, String defaultText) {
        if (conditionalTexts == null || conditionalTexts.isEmpty()) {
            return defaultText;
        }

        IQuestCapability cap = player.getCapability(QuestCapabilityProvider.QUEST_CAP).orElse(null);
        if (cap == null) {
            return defaultText;
        }

        // 遍历条件文本，返回第一个匹配条件的文本
        for (Map.Entry<String, String> entry : conditionalTexts.entrySet()) {
            String conditionKey = entry.getKey();
            String text = entry.getValue();

            if (matchesCondition(player, cap, conditionKey)) {
                return text;
            }
        }

        // 没有匹配的条件，返回默认文本
        return defaultText;
    }

    /**
     * 检查条件标识是否匹配玩家状态。
     */
    private static boolean matchesCondition(ServerPlayer player, IQuestCapability cap, String conditionKey) {
        try {
            // HAS_QUEST:quest_id
            if (conditionKey.startsWith("HAS_QUEST:")) {
                String questId = conditionKey.substring(10);
                return cap.isQuestActive(questId) || cap.isQuestCompleted(questId);
            }

            // QUEST_ACTIVE:quest_id
            if (conditionKey.startsWith("QUEST_ACTIVE:")) {
                String questId = conditionKey.substring(13);
                return cap.isQuestActive(questId);
            }

            // QUEST_COMPLETED:quest_id
            if (conditionKey.startsWith("QUEST_COMPLETED:")) {
                String questId = conditionKey.substring(16);
                return cap.isQuestCompleted(questId);
            }

            // QUEST_PHASE:quest_id|phase_id (使用 | 分隔，避免与 ResourceLocation 的 : 冲突)
            if (conditionKey.startsWith("QUEST_PHASE:")) {
                String afterPrefix = conditionKey.substring(12); // 去掉 "QUEST_PHASE:"
                int separatorIndex = afterPrefix.indexOf('|');
                
                if (separatorIndex > 0) {
                    String questId = afterPrefix.substring(0, separatorIndex);
                    String phaseId = afterPrefix.substring(separatorIndex + 1);
                    var data = cap.getActiveQuest(questId);
                    
                    // 调试日志
                    if (data != null) {
                        LOGGER.debug("[ConditionalText] QUEST_PHASE check: quest={}, phase={}, currentState={}, currentPhase={}",
                            questId, phaseId, data.getState(), data.getCurrentPhaseId());
                    } else {
                        LOGGER.debug("[ConditionalText] QUEST_PHASE check: quest={} has no active data", questId);
                    }
                    
                    // 必须同时满足：任务存在 + 处于激活状态 + 阶段ID匹配
                    boolean result = data != null 
                        && QuestState.ACTIVE.equals(data.getState())
                        && phaseId.equals(data.getCurrentPhaseId());
                    
                    LOGGER.debug("[ConditionalText] QUEST_PHASE result: {}", result);
                    return result;
                }
            }

            // NOT:inner_condition
            if (conditionKey.startsWith("NOT:")) {
                String innerCondition = conditionKey.substring(4);
                return !matchesCondition(player, cap, innerCondition);
            }

            // ALL:count （需要额外存储子条件，简化版暂不支持）
            if (conditionKey.startsWith("ALL:")) {
                // TODO: 实现 AND 组合条件
                return false;
            }

            // ANY:count （需要额外存储子条件，简化版暂不支持）
            if (conditionKey.startsWith("ANY:")) {
                // TODO: 实现 OR 组合条件
                return false;
            }

        } catch (Exception e) {
            // 条件解析失败，视为不匹配
            return false;
        }

        return false;
    }
}
