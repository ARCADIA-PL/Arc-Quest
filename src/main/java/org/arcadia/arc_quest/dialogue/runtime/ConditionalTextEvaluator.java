package org.arcadia.arc_quest.dialogue.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import org.arcadia.arc_quest.dialogue.api.ConditionalSay;
import org.arcadia.arc_quest.dialogue.api.DialogueText;
import org.arcadia.arc_quest.dialogue.api.RegisteredConditions;
import org.arcadia.arc_quest.dialogue.util.TimeSanitizer;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.capability.IQuestCapability;
import org.slf4j.Logger;

import javax.annotation.Nullable;
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
     * @param ctx              对话评估上下文（包含 player、npc、namespace 等信息）
     * @param conditionalTexts 条件文本映射（格式："priority|condition" → {@link ConditionalSay}）
     * @param defaultText      默认文本
     * @return 匹配的文本，或默认文本
     */
    public static String evaluate(DialogueEvalContext ctx, Map<String, ConditionalSay> conditionalTexts, String defaultText) {
        var result = evaluateWithSound(ctx, conditionalTexts, defaultText);
        return result.text().resolve(ctx.player(), ctx.npc()).getString();
    }

    /**
     * 检查条件标识是否匹配玩家,NPC状态。
     */
    private static boolean matchesCondition(DialogueEvalContext ctx, IQuestCapability cap, String conditionKey) {
        ServerPlayer player = ctx.player();
        Entity npc = ctx.npc();

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
                String afterPrefix = conditionKey.substring(12);
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
                return !matchesCondition(ctx, cap, innerCondition);
            }

            // ALL:cond1;cond2;cond3... （AND 组合条件）
            if (conditionKey.startsWith("ALL:")) {
                String conditionsStr = conditionKey.substring(4);
                if (conditionsStr.isEmpty()) {
                    return false;
                }
                String[] subConditions = conditionsStr.split(";");
                for (String subCond : subConditions) {
                    if (!matchesCondition(ctx, cap, subCond)) {
                        return false;
                    }
                }
                return true;
            }

            // ANY:cond1;cond2;cond3... （OR 组合条件）
            if (conditionKey.startsWith("ANY:")) {
                String conditionsStr = conditionKey.substring(4);
                if (conditionsStr.isEmpty()) {
                    return false;
                }
                String[] subConditions = conditionsStr.split(";");
                for (String subCond : subConditions) {
                    if (matchesCondition(ctx, cap, subCond)) {
                        return true;
                    }
                }
                return false;
            }

            // IS_MORNING - 检查是否是早晨（06:00-12:00，tick 0-6000）
            switch (conditionKey) {
                case "IS_MORNING" -> {
                    return TimeSanitizer.isMorning(player.level());
                }

                // IS_AFTERNOON - 检查是否是下午（12:00-18:00，tick 6000-12000）
                case "IS_AFTERNOON" -> {
                    return TimeSanitizer.isAfternoon(player.level());
                }


                // IS_NIGHT - 检查是否是夜晚（18:00-24:00，tick 12000-24000）
                case "IS_NIGHT" -> {
                    return TimeSanitizer.isNight(player.level());
                }
            }

            // GAME_TIME_IN_RANGE:startTick|endTick - 自定义时间区间
            if (conditionKey.startsWith("GAME_TIME_IN_RANGE:")) {
                String rangeStr = conditionKey.substring(19);
                String[] parts = rangeStr.split("\\|");
                if (parts.length == 2) {
                    try {
                        int startTick = Integer.parseInt(parts[0]);
                        int endTick = Integer.parseInt(parts[1]);
                        long dayTime = TimeSanitizer.sanitizeDayTime(player.level());

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

            // CUSTOM:name - 自定义条件
            if (conditionKey.startsWith("CUSTOM:")) {
                String name = conditionKey.substring(7);
                var predicate = RegisteredConditions.get(name);
                if (predicate == null) {
                    LOGGER.warn("[ConditionalText] Custom condition '{}' is not registered", name);
                    return false;
                }
                try {
                    return predicate.test(player, npc);
                } catch (Exception e) {
                    LOGGER.warn("[ConditionalText] Error evaluating custom condition '{}': {}", name, e.getMessage());
                    return false;
                }
            }

        } catch (Exception e) {
            // 条件解析失败，视为不匹配
            return false;
        }

        return false;
    }

    /**
     * 根据玩家状态评估条件文本，返回匹配到的 {@link ConditionalSay}。
     */
    public static ConditionalSay evaluateWithSound(DialogueEvalContext ctx, Map<String, ConditionalSay> conditionalTexts, String defaultText) {
        if (conditionalTexts == null || conditionalTexts.isEmpty()) {
            return ConditionalSay.of("default", DialogueText.literal(defaultText));
        }

        IQuestCapability cap = ctx.questCap();
        List<SayMatch> matches = new ArrayList<>();

        for (Map.Entry<String, ConditionalSay> entry : conditionalTexts.entrySet()) {
            String key = entry.getKey();
            ConditionalSay say = entry.getValue();

            int priority = 0;
            String conditionKey = key;
            int separatorIndex = key.indexOf('|');
            if (separatorIndex > 0) {
                try {
                    priority = Integer.parseInt(key.substring(0, separatorIndex));
                    conditionKey = key.substring(separatorIndex + 1);
                } catch (NumberFormatException e) {
                    LOGGER.warn("[ConditionalText] Failed to parse priority from key: {}", key);
                }
            }

            if (matchesCondition(ctx, cap, conditionKey)) {
                matches.add(new SayMatch(say, priority));
            }
        }

        if (matches.isEmpty()) {
            return ConditionalSay.of("default", DialogueText.literal(defaultText));
        }

        int maxPriority = matches.stream().mapToInt(m -> m.priority).max().orElse(0);
        for (SayMatch match : matches) {
            if (match.priority == maxPriority) {
                return match.say;
            }
        }
        return ConditionalSay.of("default", DialogueText.literal(defaultText));
    }

    /**
     * 获取匹配到的音效（便捷方法）。
     */
    public static SoundEvent getMatchedSound(DialogueEvalContext ctx, Map<String, ConditionalSay> conditionalTexts) {
        var result = evaluateWithSound(ctx, conditionalTexts, "");
        return result.soundEvent();
    }

    /**
     * 评估 SayIf 条件并返回完整结果（包含选中索引）。
     * <p>
     * 此方法用于事件触发，让附属模组知道哪个 SayIf 分支被选中。
     * </p>
     *
     * @param ctx              评估上下文
     * @param conditionalTexts SayIf 映射表
     * @param defaultText      默认文本
     * @return 包含选中索引、文本和音效的结果对象
     */
    public static SayIfResult evaluateWithIndex(DialogueEvalContext ctx, Map<String, ConditionalSay> conditionalTexts, String defaultText) {
        if (conditionalTexts == null || conditionalTexts.isEmpty()) {
            return new SayIfResult(null, -1, defaultText, null);
        }

        IQuestCapability cap = ctx.questCap();
        List<SayMatch> matches = new ArrayList<>();
        int matchIndex = -1;
        String matchedSayId = null;
        int currentIndex = 0;

        for (Map.Entry<String, ConditionalSay> entry : conditionalTexts.entrySet()) {
            String key = entry.getKey();
            ConditionalSay say = entry.getValue();

            int priority = 0;
            String conditionKey = key;
            int separatorIndex = key.indexOf('|');
            if (separatorIndex > 0) {
                try {
                    priority = Integer.parseInt(key.substring(0, separatorIndex));
                    conditionKey = key.substring(separatorIndex + 1);
                } catch (NumberFormatException e) {
                    LOGGER.warn("[ConditionalText] Failed to parse priority from key: {}", key);
                }
            }

            if (matchesCondition(ctx, cap, conditionKey)) {
                matches.add(new SayMatch(say, priority));
                if (matchIndex == -1) {
                    matchIndex = currentIndex;  // 记录第一个匹配的索引
                    matchedSayId = say.sayId();  // 记录 SayIf ID
                }
            }
            currentIndex++;
        }

        if (matches.isEmpty()) {
            return new SayIfResult(null, -1, defaultText, null);
        }

        int maxPriority = matches.stream().mapToInt(m -> m.priority).max().orElse(0);
        for (SayMatch match : matches) {
            if (match.priority == maxPriority) {
                return new SayIfResult(matchedSayId, matchIndex, match.say.text().resolve(ctx.player(), ctx.npc()).getString(), match.say.soundEvent());
            }
        }
        return new SayIfResult(null, -1, defaultText, null);
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

    /**
     * SayIf 评估结果封装。
     */
    public static class SayIfResult {
        /**
         * 选中的 SayIf ID（可能为 null）。
         */
        @Nullable
        public final String sayId;

        /**
         * 选中的 SayIf 分支索引（从 0 开始，-1 表示无 SayIf）。
         */
        public final int selectedIndex;

        public final String text;
        public final SoundEvent sound;

        public SayIfResult(@Nullable String sayId, int selectedIndex, String text, SoundEvent sound) {
            this.sayId = sayId;
            this.selectedIndex = selectedIndex;
            this.text = text;
            this.sound = sound;
        }
    }

    /**
     * 内部类：SayIf 匹配结果
     */
    private static class SayMatch {
        final ConditionalSay say;
        final int priority;

        SayMatch(ConditionalSay say, int priority) {
            this.say = say;
            this.priority = priority;
        }
    }
}
