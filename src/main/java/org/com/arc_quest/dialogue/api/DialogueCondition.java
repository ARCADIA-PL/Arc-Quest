package org.com.arc_quest.dialogue.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.dialogue.runtime.DialogueEvalContext;
import org.com.arc_quest.quest.api.CompareOp;
import org.com.arc_quest.quest.api.ICondition;
import org.com.arc_quest.quest.api.PhaseDefinition;
import org.com.arc_quest.quest.api.QuestDefinition;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestRuntimeData;
import org.com.arc_quest.quest.registry.QuestRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * 对话条件 —— 控制选项/文本的可见性。
 */
public sealed interface DialogueCondition permits
        // ── 逻辑组合 ──
        DialogueCondition.Not,
        DialogueCondition.All,
        DialogueCondition.Any,
        // ── 自定义条件 ──
        DialogueCondition.CustomCondition,
        // ── 任务状态 ──
        DialogueCondition.HasQuest,
        DialogueCondition.QuestActive,
        DialogueCondition.QuestCompleted,
        DialogueCondition.QuestFailed,
        DialogueCondition.QuestPhase,              // 兼容旧语义（= Active）
        DialogueCondition.QuestPhaseActive,
        DialogueCondition.QuestPhaseCompleted,
        DialogueCondition.QuestPhaseReached,
        DialogueCondition.PhaseBefore,
        DialogueCondition.PhaseAfter,
        DialogueCondition.PhaseBetween,
        DialogueCondition.AnyActiveInRange,
        DialogueCondition.AllCompletedInRange,
        DialogueCondition.PhaseEnterable,
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
        DialogueCondition.DialogueOnCooldown,
        // ── 跨系统适配器 ──
        DialogueCondition.IConditionWrapper {

    /**
     * 评估条件是否满足。
     */
    boolean test(DialogueEvalContext ctx);

    // ═══════════════════════════════════════════════
    //  内部辅助
    // ═══════════════════════════════════════════════

    private static QuestRuntimeData getQuestData(DialogueEvalContext ctx, String questId) {
        return ctx.questCap().getActiveQuest(questId);
    }

    private static QuestDefinition getQuestDef(String questId) {
        ResourceLocation rl = ResourceLocation.tryParse(questId);
        if (rl == null) return null;
        return QuestRegistry.get(rl);
    }

    private static boolean isPhaseReached(QuestRuntimeData data, String phaseId) {
        return data.isPhaseActive(phaseId) || data.isPhaseCompleted(phaseId);
    }

    /**
     * 返回 [from, to) 的有序区间（按声明顺序）。
     * 若 from/to 不存在或区间为空，返回 null。
     */
    private static int[] resolveRange(QuestDefinition def, String fromPhaseId, String toPhaseId) {
        List<String> ids = new ArrayList<>(def.getPhaseIds());
        int a = ids.indexOf(fromPhaseId);
        int b = ids.indexOf(toPhaseId);
        if (a < 0 || b < 0) return null;

        int from = Math.min(a, b);
        int to = Math.max(a, b);

        if (from == to) return null; // 空区间
        return new int[]{from, to};  // [from, to)
    }

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

    /**
     * 自定义条件
     */
    record CustomCondition(String nameOrPredicate) implements DialogueCondition {

        private static final String AUTO_PREFIX = "auto_";

        public static CustomCondition create(BiPredicate<ServerPlayer, Entity> predicate) {
            String name = RegisteredConditions.autoRegister(predicate);
            return new CustomCondition(name);
        }

        public CustomCondition {
            if (!nameOrPredicate.startsWith(AUTO_PREFIX)) {
                if (!RegisteredConditions.isRegistered(nameOrPredicate)) {
                    Arc_quest.LOGGER.warn("[CustomCondition] Condition '{}' is not registered", nameOrPredicate);
                }
            }
        }

        @Override
        public boolean test(DialogueEvalContext ctx) {
            BiPredicate<ServerPlayer, Entity> predicate = RegisteredConditions.get(nameOrPredicate);
            if (predicate == null) {
                Arc_quest.LOGGER.warn("[CustomCondition] Condition '{}' is not found", nameOrPredicate);
                return false;
            }
            try {
                return predicate.test(ctx.player(), ctx.npc());
            } catch (Exception e) {
                Arc_quest.LOGGER.warn("[CustomCondition] Error evaluating condition '{}': {}", nameOrPredicate, e.getMessage());
                return false;
            }
        }
    }

    // ═══════════════════════════════════════════════
    //  任务状态（并行 phase 语义）
    // ═══════════════════════════════════════════════

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
     * 向后兼容：旧 QuestPhase = 判断该 phase 是否 active
     */
    record QuestPhase(String questId, String phaseId) implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            QuestRuntimeData data = getQuestData(ctx, questId);
            if (data == null) return false;
            return data.isPhaseActive(phaseId);
        }
    }

    /**
     * 原子条件：phase 是否 active
     */
    record QuestPhaseActive(String questId, String phaseId) implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            QuestRuntimeData data = getQuestData(ctx, questId);
            if (data == null) return false;
            return data.isPhaseActive(phaseId);
        }
    }

    /**
     * 原子条件：phase 是否 completed
     */
    record QuestPhaseCompleted(String questId, String phaseId) implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            QuestRuntimeData data = getQuestData(ctx, questId);
            if (data == null) return false;
            return data.isPhaseCompleted(phaseId);
        }
    }

    /**
     * 原子条件：phase 是否 reached（active 或 completed）
     */
    record QuestPhaseReached(String questId, String phaseId) implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            QuestRuntimeData data = getQuestData(ctx, questId);
            if (data == null) return false;
            return isPhaseReached(data, phaseId);
        }
    }

    /**
     * 区间语义（声明顺序 + 集合语义）
     * PhaseBefore(target) => !isPhaseReached(target)
     */
    record PhaseBefore(String questId, String targetPhaseId) implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            QuestRuntimeData data = getQuestData(ctx, questId);
            if (data == null) return false;
            return !isPhaseReached(data, targetPhaseId);
        }
    }

    /**
     * 区间语义（声明顺序 + 集合语义）
     * PhaseAfter(target) => isPhaseCompleted(target)
     */
    record PhaseAfter(String questId, String targetPhaseId) implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            QuestRuntimeData data = getQuestData(ctx, questId);
            if (data == null) return false;
            return data.isPhaseCompleted(targetPhaseId);
        }
    }

    /**
     * 区间语义（声明顺序 + 集合语义）
     * PhaseBetween(start, end) => isPhaseReached(start) && !isPhaseReached(end)
     */
    record PhaseBetween(String questId, String startPhaseId, String endPhaseId) implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            QuestRuntimeData data = getQuestData(ctx, questId);
            if (data == null) return false;
            return isPhaseReached(data, startPhaseId) && !isPhaseReached(data, endPhaseId);
        }
    }

    /**
     * 严格增强：区间 [start, end)（按声明顺序）内，是否存在任意 active phase
     */
    record AnyActiveInRange(String questId, String startPhaseId, String endPhaseId) implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            QuestRuntimeData data = getQuestData(ctx, questId);
            if (data == null) return false;

            QuestDefinition def = getQuestDef(questId);
            if (def == null) return false;

            int[] r = resolveRange(def, startPhaseId, endPhaseId);
            if (r == null) return false;

            List<String> ids = new ArrayList<>(def.getPhaseIds());
            for (int i = r[0]; i < r[1]; i++) {
                if (data.isPhaseActive(ids.get(i))) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * 严格增强：区间 [start, end)（按声明顺序）内，是否全部 completed
     */
    record AllCompletedInRange(String questId, String startPhaseId, String endPhaseId) implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            QuestRuntimeData data = getQuestData(ctx, questId);
            if (data == null) return false;

            QuestDefinition def = getQuestDef(questId);
            if (def == null) return false;

            int[] r = resolveRange(def, startPhaseId, endPhaseId);
            if (r == null) return false;

            List<String> ids = new ArrayList<>(def.getPhaseIds());
            boolean hasAny = false;
            for (int i = r[0]; i < r[1]; i++) {
                hasAny = true;
                if (!data.isPhaseCompleted(ids.get(i))) {
                    return false;
                }
            }
            return hasAny;
        }
    }

    /**
     * 当前是否“可进入某 phase”（受 enterCondition 约束）
     */
    record PhaseEnterable(String questId, String phaseId) implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            IQuestCapability cap = ctx.questCap();
            QuestRuntimeData data = cap.getActiveQuest(questId);
            if (data == null) return false;

            if (data.isPhaseActive(phaseId) || data.isPhaseCompleted(phaseId)) {
                return false;
            }

            QuestDefinition def = getQuestDef(questId);
            if (def == null) return false;

            PhaseDefinition phase = def.getPhase(phaseId);
            if (phase == null) return false;

            ICondition cond = phase.getEnterCondition();
            return cond == null || cond.test(
                    ctx.player(),
                    cap.getCompletedQuestLocations(),
                    cap.getAllFlags(),
                    cap.getAllVariables()
            );
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

    record VariableCheck(String key, String op, int value) implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            int actual = ctx.questCap().getVariable(key);
            return CompareOp.fromSymbol(op).evaluate(actual, value);
        }
    }

    // ═══════════════════════════════════════════════
    //  时间条件
    // ═══════════════════════════════════════════════

    record IsMorning() implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            long t = ctx.dayTimeTick();
            return t >= 0 && t < 6000;
        }
    }

    record IsAfternoon() implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            long t = ctx.dayTimeTick();
            return t >= 6000 && t < 12000;
        }
    }

    record IsNight() implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            long t = ctx.dayTimeTick();
            return t >= 12000 && t < 24000;
        }
    }

    record GameTimeInRange(int startTick, int endTick) implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            long t = ctx.dayTimeTick();
            if (startTick <= endTick) {
                return t >= startTick && t <= endTick;
            } else {
                return endTick == 0 ? t >= startTick : (t >= startTick || t < endTick);
            }
        }
    }

    // ═══════════════════════════════════════════════
    //  对话历史（自动使用 context 中的 namespace）
    // ═══════════════════════════════════════════════

    record NodeVisited(String nodeId) implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            return ctx.progress().hasVisitedNode(ctx.namespace(), nodeId);
        }
    }

    record ChoiceSelected(String nodeId, int choiceIndex) implements DialogueCondition {
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
                } catch (NumberFormatException ignored) {
                }
            }
            return 0;
        }

        @Override
        public boolean test(DialogueEvalContext ctx) {
            return ctx.progress().hasSelectedChoice(ctx.namespace(), nodeId, choiceIndex);
        }
    }

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

    record ChoiceOnCooldown(String nodeId, int choiceIndex, int cooldownSeconds) implements DialogueCondition {
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

    /**
     * 跨系统适配器：将 ICondition 包装为 DialogueCondition
     */
    record IConditionWrapper(ICondition condition) implements DialogueCondition {
        @Override
        public boolean test(DialogueEvalContext ctx) {
            return condition.test(
                    ctx.player(),
                    ctx.questCap().getCompletedQuestLocations(),
                    ctx.questCap().getAllFlags(),
                    ctx.questCap().getAllVariables()
            );
        }
    }
}