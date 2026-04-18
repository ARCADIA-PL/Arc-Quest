package org.com.arc_quest.dialogue.api;

import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.dialogue.runtime.DialogueEvalContext;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestRuntimeData;

import java.util.List;

/**
 * 对话条件 —— 控制选项/文本的可见性。
 */
public sealed interface DialogueCondition permits
        // ── 逻辑组合 ──
        DialogueCondition.Not,
        DialogueCondition.All,
        DialogueCondition.Any,
        // ── 任务状态 ──
        DialogueCondition.HasQuest,
        DialogueCondition.QuestActive,
        DialogueCondition.QuestCompleted,
        DialogueCondition.QuestFailed,
        DialogueCondition.QuestPhase,
        // ── Flag / Variable ──
        DialogueCondition.HasFlag,
        DialogueCondition.VariableCheck,
        // ── 时间 ──
        DialogueCondition.IsMorning,
        DialogueCondition.IsAfternoon,
        DialogueCondition.IsNight,
        DialogueCondition.GameTimeInRange,
        // ── 对话历史 ──
        DialogueCondition.NodeVisited,
        DialogueCondition.ChoiceSelected,
        DialogueCondition.DialogueCompleted,
        // ── 冷却 ──
        DialogueCondition.NodeOnCooldown,
        DialogueCondition.ChoiceOnCooldown,
        DialogueCondition.DialogueOnCooldown {

    /**
     * 评估条件是否满足。
     *
     * @param ctx 评估上下文（包含 player、npc、namespace、progress 等）
     * @return true = 条件满足
     */
    boolean test(DialogueEvalContext ctx);

    // ═══════════════════════════════════════════════
    //  逻辑组合
    // ═══════════════════════════════════════════════

    record Not(DialogueCondition inner) implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            return !inner.test(ctx);
        }
    }

    record All(List<DialogueCondition> conditions) implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            return conditions.stream().allMatch(c -> c.test(ctx));
        }
    }

    record Any(List<DialogueCondition> conditions) implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            return conditions.stream().anyMatch(c -> c.test(ctx));
        }
    }

    // ═══════════════════════════════════════════════
    //  任务状态
    // ═══════════════════════════════════════════════

    /**
     * 玩家是否拥有该任务（活跃 OR 已完成 OR 已失败）
     */
    record HasQuest(String questId) implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            IQuestCapability cap = ctx.questCap();
            return cap.isQuestActive(questId) || cap.isQuestCompleted(questId) || cap.isQuestFailed(questId);
        }
    }

    record QuestActive(String questId) implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            return ctx.questCap().isQuestActive(questId);
        }
    }

    record QuestCompleted(String questId) implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            return ctx.questCap().isQuestCompleted(questId);
        }
    }

    record QuestFailed(String questId) implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            return ctx.questCap().isQuestFailed(questId);
        }
    }

    /**
     * 任务处于指定阶段
     */
    record QuestPhase(String questId, String phaseId) implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            QuestRuntimeData data = ctx.questCap().getActiveQuest(questId);
            if (data == null) return false;
            return phaseId.equals(data.getCurrentPhaseId());
        }
    }

    // ═══════════════════════════════════════════════
    //  Flag / Variable
    // ═══════════════════════════════════════════════

    record HasFlag(String flag) implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            return ctx.questCap().hasFlag(flag);
        }
    }

    /**
     * 变量数值比较。
     *
     * @param key   变量名
     * @param op    比较操作符: "==", "!=", ">", ">=", "<", "<="
     * @param value 比较目标值
     */
    record VariableCheck(String key, String op, int value) implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            int actual = ctx.questCap().getVariable(key);
            return switch (op) {
                case "==" -> actual == value;
                case "!=" -> actual != value;
                case ">" -> actual > value;
                case ">=" -> actual >= value;
                case "<" -> actual < value;
                case "<=" -> actual <= value;
                default -> false;
            };
        }
    }

    // ═══════════════════════════════════════════════
    //  时间条件
    // ═══════════════════════════════════════════════

    /**
     * 早晨：tick [0, 6000) = 游戏时间 6:00-12:00
     * <p>
     * Minecraft 时间映射：
     * <ul>
     *   <li>tick 0 = 早上6:00（日出）</li>
     *   <li>tick 6000 = 中午12:00</li>
     * </ul>
     */
    record IsMorning() implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            long t = ctx.dayTimeTick();
            boolean result = t >= 0 && t < 6000;
            Arc_quest.LOGGER.info("[DEBUG-Time] IsMorning: dayTimeTick={}, inRange=[0,6000), result={}", t, result);
            return result;
        }
    }

    /**
     * 下午：tick [6000, 12000) = 游戏时间 12:00-18:00
     * <p>
     * Minecraft 时间映射：
     * <ul>
     *   <li>tick 6000 = 中午12:00</li>
     *   <li>tick 12000 = 晚上18:00（日落）</li>
     * </ul>
     */
    record IsAfternoon() implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            long t = ctx.dayTimeTick();
            boolean result = t >= 6000 && t < 12000;
            Arc_quest.LOGGER.info("[DEBUG-Time] IsAfternoon: dayTimeTick={}, inRange=[6000,12000), result={}", t, result);
            return result;
        }
    }

    /**
     * 夜晚：tick [12000, 24000) = 游戏时间 18:00-次日6:00
     * <p>
     * Minecraft 时间映射：
     * <ul>
     *   <li>tick 12000 = 晚上18:00（日落）</li>
     *   <li>tick 18000 = 凌晨0:00</li>
     *   <li>tick 24000/0 = 次日早上6:00（日出）</li>
     * </ul>
     */
    record IsNight() implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            long t = ctx.dayTimeTick();
            boolean result = t >= 12000;
            Arc_quest.LOGGER.info("[DEBUG-Time] IsNight: dayTimeTick={}, inRange=[12000,24000), result={}", t, result);
            return result;
        }
    }

    /**
     * 游戏时间在指定刻区间内（支持跨天）。
     *
     * @param startTick 起始刻 [0, 23999]
     * @param endTick   结束刻 [0, 23999]；若 start > end 则视为跨天区间
     */
    record GameTimeInRange(int startTick, int endTick) implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            long t = ctx.dayTimeTick();
            if (startTick <= endTick) {
                return t >= startTick && t <= endTick;
            } else {
                // 跨天：如 22:00 (16000) → 06:00 (0)
                return t >= startTick || t <= endTick;
            }
        }
    }

    // ═══════════════════════════════════════════════
    //  对话历史（自动使用 context 中的 namespace）
    // ═══════════════════════════════════════════════

    /**
     * 节点是否已被访问过
     */
    record NodeVisited(String nodeId) implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            return ctx.progress().hasVisitedNode(ctx.namespace(), nodeId);
        }
    }

    /**
     * 选项是否已被选择过。
     *
     * @param nodeId      节点 ID
     * @param choiceIndex 选项索引
     */
    record ChoiceSelected(String nodeId, int choiceIndex) implements DialogueCondition {
        /**
         * 向后兼容：旧格式 "nodeId:index"
         */
        public ChoiceSelected(String legacyKey) {
            this(parseLegacyNode(legacyKey), parseLegacyIndex(legacyKey));
        }

        private static String parseLegacyNode(String key) {
            int i = key.lastIndexOf(':');
            return i >= 0 ? key.substring(0, i) : key;
        }

        private static int parseLegacyIndex(String key) {
            int i = key.lastIndexOf(':');
            if (i >= 0 && i < key.length() - 1) {
                try {
                    return Integer.parseInt(key.substring(i + 1));
                } catch (NumberFormatException e) { /* ignore */ }
            }
            return 0;
        }

        @Override
        public boolean test(DialogueEvalContext ctx) {
            return ctx.progress().hasSelectedChoice(ctx.namespace(), nodeId, choiceIndex);
        }
    }

    /**
     * 对话树是否已完成过
     */
    record DialogueCompleted(String dialogueId) implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            return ctx.progress().hasCompletedDialogue(ctx.namespace(), dialogueId);
        }
    }

    record NodeOnCooldown(String nodeId, int cooldownSeconds) implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            return ctx.progress().isNodeOnCooldown(
                    ctx.namespace(), nodeId,
                    CooldownType.SECONDS, cooldownSeconds, 0,
                    ctx.nowRealTime(), ctx.gameTime(), ctx.dayTime());
        }
    }

    record ChoiceOnCooldown(String nodeId, int choiceIndex, int cooldownSeconds)
            implements DialogueCondition {
        public ChoiceOnCooldown(String legacyKey, int cooldownSeconds) {
            this(ChoiceSelected.parseLegacyNode(legacyKey),
                    ChoiceSelected.parseLegacyIndex(legacyKey),
                    cooldownSeconds);
        }

        @Override
        public boolean test(DialogueEvalContext ctx) {
            return ctx.progress().isChoiceOnCooldown(
                    ctx.namespace(), nodeId, choiceIndex,
                    CooldownType.SECONDS, cooldownSeconds, 0,
                    ctx.nowRealTime(), ctx.gameTime(), ctx.dayTime());
        }
    }

    record DialogueOnCooldown(String dialogueId, int cooldownSeconds) implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            return ctx.progress().isDialogueOnCooldown(
                    ctx.namespace(), dialogueId,
                    CooldownType.SECONDS, cooldownSeconds, 0,
                    ctx.nowRealTime(), ctx.gameTime(), ctx.dayTime());
        }
    }
}