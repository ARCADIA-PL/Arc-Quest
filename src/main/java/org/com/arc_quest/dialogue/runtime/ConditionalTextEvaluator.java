package org.com.arc_quest.dialogue.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.quest.api.QuestState;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 条件文本评估器 - 在服务端评估条件并选择合适的文本。
 */
public final class ConditionalTextEvaluator {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ConditionalTextEvaluator() {
    }

    /**
     * 根据玩家状态评估条件文本，返回最高优先级的匹配文本。
     *
     * @param player           玩家
     * @param conditionalTexts 条件文本映射（格式："priority|condition" → 文本）
     * @param defaultText      默认文本
     * @return 匹配的文本，或默认文本
     */
    public static String evaluate(ServerPlayer player, Map<String, String> conditionalTexts, String defaultText) {
        if (conditionalTexts == null || conditionalTexts.isEmpty()) {
            return defaultText;
        }

        IQuestCapability cap = player.getCapability(QuestCapabilityProvider.QUEST_CAP).orElse(null);

        // 收集所有匹配的条件文本及其优先级
        List<TextMatch> matches = new ArrayList<>();
        for (Map.Entry<String, String> entry : conditionalTexts.entrySet()) {
            String key = entry.getKey();
            String text = entry.getValue();

            // 解析优先级
            int priority = 0;
            String conditionKey = key;
            int separatorIndex = key.indexOf('|');
            if (separatorIndex > 0) {
                try {
                    priority = Integer.parseInt(key.substring(0, separatorIndex));
                    conditionKey = key.substring(separatorIndex + 1);
                } catch (NumberFormatException e) {
                    // 解析失败，使用默认优先级 0
                    LOGGER.warn("[ConditionalText] Failed to parse priority from key: {}", key);
                }
            }

            if (matchesCondition(player, cap, conditionKey)) {
                matches.add(new TextMatch(text, priority));
            }
        }

        // 如果没有匹配，返回默认文本
        if (matches.isEmpty()) {
            return defaultText;
        }

        // 找到最高优先级
        int maxPriority = matches.stream()
                .mapToInt(m -> m.priority)
                .max()
                .orElse(0);

        // 返回第一个最高优先级的匹配（按定义顺序）
        for (TextMatch match : matches) {
            if (match.priority == maxPriority) {
                return match.text;
            }
        }

        // 理论上不会到达这里
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
                // AND 组合条件
                return false;
            }

            // ANY:count （需要额外存储子条件，简化版暂不支持）
            if (conditionKey.startsWith("ANY:")) {
                // OR 组合条件
                return false;
            }

            // IS_MORNING - 检查是否是早晨（06:00-12:00，tick 0-6000）
            switch (conditionKey) {
                case "IS_MORNING" -> {
                    player.level();
                    long dayTime = player.level().getDayTime() % 24000;
                    return dayTime >= 0 && dayTime < 6000;
                }

                // IS_AFTERNOON - 检查是否是下午（12:00-18:00，tick 6000-12000）
                case "IS_AFTERNOON" -> {
                    player.level();
                    long dayTime = player.level().getDayTime() % 24000;
                    return dayTime >= 6000 && dayTime < 12000;
                }


                // IS_NIGHT - 检查是否是夜晚（18:00-次日06:00，tick 12000-0，跨天区间）
                case "IS_NIGHT" -> {
                    player.level();
                    long dayTime = player.level().getDayTime() % 24000;
                    return dayTime >= 12000 || dayTime < 6000;  // 跨天区间：18:00-24:00 或 00:00-06:00

                }
            }

            // GAME_TIME_IN_RANGE:startTick|endTick - 自定义时间区间
            if (conditionKey.startsWith("GAME_TIME_IN_RANGE:")) {
                player.level();
                String rangeStr = conditionKey.substring(19);  // 去掉 "GAME_TIME_IN_RANGE:"
                String[] parts = rangeStr.split("\\|");
                if (parts.length == 2) {
                    try {
                        int startTick = Integer.parseInt(parts[0]);
                        int endTick = Integer.parseInt(parts[1]);
                        long dayTime = player.level().getDayTime() % 24000;

                        if (startTick < endTick) {
                            // 正常区间
                            return dayTime >= startTick && dayTime < endTick;
                        } else {
                            // 跨天区间
                            return dayTime >= startTick || dayTime < endTick;
                        }
                    } catch (NumberFormatException e) {
                        LOGGER.warn("[ConditionalText] Failed to parse time range: {}", conditionKey);
                        return false;
                    }
                }
            }

        } catch (Exception e) {
            // 条件解析失败，视为不匹配
            return false;
        }

        return false;
    }

    /**
     * 内部类：文本匹配结果
     */
    private static class TextMatch {
        final String text;
        final int priority;

        TextMatch(String text, int priority) {
            this.text = text;
            this.priority = priority;
        }
    }
}
