package org.arcadia.arc_quest.condition;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.ForgeRegistries;
import org.arcadia.arc_quest.dialogue.api.DialogueCondition;
import org.arcadia.arc_quest.quest.api.CompareOp;
import org.arcadia.arc_quest.quest.api.ICondition;
import org.arcadia.arc_quest.capability.ArcQuestPlayerManager;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 统一条件桥接层。
 * <p>
 * 将统一的 {@link ConditionSpec} 转换为：
 * <ul>
 *   <li>{@link ICondition} —— 供任务系统使用</li>
 *   <li>{@link DialogueCondition} —— 供对话系统使用</li>
 * </ul>
 * <p>
 * 同时提供便捷的列表转换方法。
 */
public final class ConditionBridge {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ConditionBridge() {}

    // ═══════════════════════════════════════════════
    //  ICondition 转换
    // ═══════════════════════════════════════════════

    @Nullable
    public static ICondition toQuestCondition(@Nullable ConditionSpec spec) {
        if (spec == null || spec.isAlways()) return null;

        String cond = spec.condition;
        if (cond == null || cond.isBlank()) return null;

        if (cond.startsWith("minecraft:")) {
            return toVanillaPredicateCondition(spec);
        }

        return switch (cond) {
            case "arc_quest:always" -> null;
            case "arc_quest:quest_completed" -> ICondition.questCompleted(ResourceLocation.parse(spec.questId));
            case "arc_quest:quest_accepted" -> new QuestAcceptedCondition(spec.questId);
            case "arc_quest:quest_not_started" -> new QuestNotStartedCondition(spec.questId);
            case "arc_quest:quest_phase" -> new QuestPhaseCondition(spec.questId, spec.phaseId);
            case "arc_quest:quest_phase_completed" -> new QuestPhaseCompletedCondition(spec.questId, spec.phaseId);
            case "arc_quest:quest_phase_reached" -> new QuestPhaseReachedCondition(spec.questId, spec.phaseId);
            case "arc_quest:has_flag" -> ICondition.flagSet(spec.flag);
            case "arc_quest:not_has_flag" -> ICondition.flagNotSet(spec.flag);
            case "arc_quest:variable_check" -> ICondition.variable(spec.key, CompareOp.fromSymbol(spec.op), spec.value);
            case "arc_quest:and" -> toAndCondition(spec);
            case "arc_quest:or" -> toOrCondition(spec);
            case "arc_quest:not" -> toNotCondition(spec);
            case "arc_quest:has_effect" -> {
                MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(ResourceLocation.parse(spec.effectId));
                yield effect != null ? new HasEffectCondition(effect) : null;
            }
            case "arc_quest:xp_level" -> new XpLevelCondition(spec.count);
            default -> {
                LOGGER.warn("[ConditionBridge] Unknown quest condition type: {}", cond);
                yield null;
            }
        };
    }

    public static List<ICondition> toQuestConditions(@Nullable List<ConditionSpec> specs) {
        List<ICondition> result = new ArrayList<>();
        if (specs == null) return result;
        for (ConditionSpec spec : specs) {
            ICondition cond = toQuestCondition(spec);
            if (cond != null) result.add(cond);
        }
        return result;
    }

    // ═══════════════════════════════════════════════
    //  DialogueCondition 转换
    // ═══════════════════════════════════════════════

    @Nullable
    public static DialogueCondition toDialogueCondition(@Nullable ConditionSpec spec) {
        if (spec == null || spec.isAlways()) return null;

        String cond = spec.condition;
        if (cond == null || cond.isBlank()) return null;

        if (cond.startsWith("minecraft:")) {
            return new DialogueCondition.CustomCondition(spec.condition);
        }

        return switch (cond) {
            case "arc_quest:always" -> null;
            case "arc_quest:quest_completed" -> new DialogueCondition.QuestCompleted(spec.questId);
            case "arc_quest:quest_accepted" -> new DialogueCondition.QuestActive(spec.questId);
            case "arc_quest:quest_not_started" -> new DialogueCondition.Not(new DialogueCondition.HasQuest(spec.questId));
            case "arc_quest:has_quest" -> new DialogueCondition.HasQuest(spec.questId);
            case "arc_quest:quest_failed" -> new DialogueCondition.QuestFailed(spec.questId);
            case "arc_quest:quest_phase" -> new DialogueCondition.QuestPhase(spec.questId, spec.phaseId);
            case "arc_quest:quest_phase_completed" -> new DialogueCondition.QuestPhaseCompleted(spec.questId, spec.phaseId);
            case "arc_quest:quest_phase_reached" -> new DialogueCondition.QuestPhaseReached(spec.questId, spec.phaseId);
            case "arc_quest:phase_before" -> new DialogueCondition.PhaseBefore(spec.questId, spec.targetPhaseId);
            case "arc_quest:phase_after" -> new DialogueCondition.PhaseAfter(spec.questId, spec.targetPhaseId);
            case "arc_quest:phase_between" -> new DialogueCondition.PhaseBetween(spec.questId, spec.fromPhaseId, spec.toPhaseId);
            case "arc_quest:any_active_in_range" -> new DialogueCondition.AnyActiveInRange(spec.questId, spec.fromPhaseId, spec.toPhaseId);
            case "arc_quest:all_completed_in_range" -> new DialogueCondition.AllCompletedInRange(spec.questId, spec.fromPhaseId, spec.toPhaseId);
            case "arc_quest:phase_enterable" -> new DialogueCondition.PhaseEnterable(spec.questId, spec.phaseId);
            case "arc_quest:has_flag" -> new DialogueCondition.HasFlag(spec.flag);
            case "arc_quest:not_has_flag" -> new DialogueCondition.Not(new DialogueCondition.HasFlag(spec.flag));
            case "arc_quest:variable_check" -> new DialogueCondition.VariableCheck(spec.key, spec.op, spec.value);
            case "arc_quest:is_morning" -> new DialogueCondition.IsMorning();
            case "arc_quest:is_afternoon" -> new DialogueCondition.IsAfternoon();
            case "arc_quest:is_night" -> new DialogueCondition.IsNight();
            case "arc_quest:game_time_in_range" -> new DialogueCondition.GameTimeInRange(spec.startTick, spec.endTick);
            case "arc_quest:node_visited" -> new DialogueCondition.NodeVisited(spec.nodeId);
            case "arc_quest:choice_selected" -> new DialogueCondition.ChoiceSelected(spec.choiceId);
            case "arc_quest:dialogue_completed" -> new DialogueCondition.DialogueCompleted(spec.dialogueId);
            case "arc_quest:node_on_cooldown" -> new DialogueCondition.NodeOnCooldown(spec.nodeId, (int) spec.cooldownSeconds);
            case "arc_quest:choice_on_cooldown" -> new DialogueCondition.ChoiceOnCooldown(spec.choiceId, (int) spec.cooldownSeconds);
            case "arc_quest:dialogue_on_cooldown" -> new DialogueCondition.DialogueOnCooldown(spec.dialogueId, (int) spec.cooldownSeconds);
            case "arc_quest:custom" -> new DialogueCondition.CustomCondition(spec.name);
            case "arc_quest:and" -> toDialogueAllCondition(spec);
            case "arc_quest:or" -> toDialogueAnyCondition(spec);
            case "arc_quest:not" -> toDialogueNotCondition(spec);
            default -> {
                LOGGER.warn("[ConditionBridge] Unknown dialogue condition type: {}", cond);
                yield null;
            }
        };
    }

    public static List<DialogueCondition> toDialogueConditions(@Nullable List<ConditionSpec> specs) {
        List<DialogueCondition> result = new ArrayList<>();
        if (specs == null) return result;
        for (ConditionSpec spec : specs) {
            DialogueCondition cond = toDialogueCondition(spec);
            if (cond != null) result.add(cond);
        }
        return result;
    }

    // ═══════════════════════════════════════════════
    //  组合条件辅助
    // ═══════════════════════════════════════════════

    @Nullable
    private static ICondition toAndCondition(ConditionSpec spec) {
        List<ConditionSpec> subs = spec.conditions;
        if (subs == null || subs.isEmpty()) return null;
        ICondition result = null;
        for (ConditionSpec sub : subs) {
            ICondition subCond = toQuestCondition(sub);
            if (subCond == null) continue;
            result = result == null ? subCond : result.and(subCond);
        }
        return result;
    }

    @Nullable
    private static ICondition toOrCondition(ConditionSpec spec) {
        List<ConditionSpec> subs = spec.conditions;
        if (subs == null || subs.isEmpty()) return null;
        ICondition result = null;
        for (ConditionSpec sub : subs) {
            ICondition subCond = toQuestCondition(sub);
            if (subCond == null) continue;
            result = result == null ? subCond : result.or(subCond);
        }
        return result;
    }

    @Nullable
    private static ICondition toNotCondition(ConditionSpec spec) {
        ICondition inner = toQuestCondition(spec.inner);
        return inner != null ? inner.negate() : null;
    }

    @Nullable
    private static DialogueCondition toDialogueAllCondition(ConditionSpec spec) {
        List<ConditionSpec> subs = spec.conditions;
        if (subs == null || subs.isEmpty()) return null;
        List<DialogueCondition> conds = new ArrayList<>();
        for (ConditionSpec sub : subs) {
            DialogueCondition subCond = toDialogueCondition(sub);
            if (subCond != null) conds.add(subCond);
        }
        return conds.isEmpty() ? null : new DialogueCondition.All(conds);
    }

    @Nullable
    private static DialogueCondition toDialogueAnyCondition(ConditionSpec spec) {
        List<ConditionSpec> subs = spec.conditions;
        if (subs == null || subs.isEmpty()) return null;
        List<DialogueCondition> conds = new ArrayList<>();
        for (ConditionSpec sub : subs) {
            DialogueCondition subCond = toDialogueCondition(sub);
            if (subCond != null) conds.add(subCond);
        }
        return conds.isEmpty() ? null : new DialogueCondition.Any(conds);
    }

    @Nullable
    private static DialogueCondition toDialogueNotCondition(ConditionSpec spec) {
        DialogueCondition inner = toDialogueCondition(spec.inner);
        return inner != null ? new DialogueCondition.Not(inner) : null;
    }

    // ═══════════════════════════════════════════════
    //  条件序列化（供 ConditionalTextEvaluator 使用）
    // ═══════════════════════════════════════════════

    /**
     * 将 {@link DialogueCondition} 序列化为条件键字符串，
     * 格式与 {@code ConditionalTextEvaluator.matchesCondition()} 的解析逻辑一致。
     */
    public static String serializeConditionKey(DialogueCondition condition) {
        if (condition instanceof DialogueCondition.HasQuest q) {
            return "HAS_QUEST:" + q.questId();
        } else if (condition instanceof DialogueCondition.QuestActive q) {
            return "QUEST_ACTIVE:" + q.questId();
        } else if (condition instanceof DialogueCondition.QuestCompleted q) {
            return "QUEST_COMPLETED:" + q.questId();
        } else if (condition instanceof DialogueCondition.QuestPhase q) {
            return "QUEST_PHASE:" + q.questId() + "|" + q.phaseId();
        } else if (condition instanceof DialogueCondition.Not n) {
            return "NOT:" + serializeConditionKey(n.inner());
        } else if (condition instanceof DialogueCondition.All a) {
            StringBuilder sb = new StringBuilder("ALL:");
            for (int i = 0; i < a.conditions().size(); i++) {
                if (i > 0) sb.append(";");
                sb.append(serializeConditionKey(a.conditions().get(i)));
            }
            return sb.toString();
        } else if (condition instanceof DialogueCondition.Any any) {
            StringBuilder sb = new StringBuilder("ANY:");
            for (int i = 0; i < any.conditions().size(); i++) {
                if (i > 0) sb.append(";");
                sb.append(serializeConditionKey(any.conditions().get(i)));
            }
            return sb.toString();
        } else if (condition instanceof DialogueCondition.CustomCondition cc) {
            return "CUSTOM:" + cc.nameOrPredicate();
        } else if (condition instanceof DialogueCondition.IsMorning) {
            return "IS_MORNING";
        } else if (condition instanceof DialogueCondition.IsAfternoon) {
            return "IS_AFTERNOON";
        } else if (condition instanceof DialogueCondition.IsNight) {
            return "IS_NIGHT";
        } else if (condition instanceof DialogueCondition.GameTimeInRange g) {
            return "GAME_TIME_IN_RANGE:" + g.startTick() + "|" + g.endTick();
        }
        return "UNKNOWN";
    }

    /**
     * 将条件 Spec 列表编译为单个序列化条件键。
     * 多条条件以 AND 语义组合。
     */
    @Nullable
    public static String compileConditionKey(@Nullable List<ConditionSpec> specs) {
        if (specs == null || specs.isEmpty()) return null;
        List<DialogueCondition> conds = toDialogueConditions(specs);
        if (conds.isEmpty()) return null;
        DialogueCondition combined = conds.size() == 1
                ? conds.get(0)
                : new DialogueCondition.All(conds);
        return serializeConditionKey(combined);
    }

    // ═══════════════════════════════════════════════
    //  原版 predicate → ICondition
    // ═══════════════════════════════════════════════

    private static ICondition toVanillaPredicateCondition(ConditionSpec spec) {
        return new VanillaPredicateCondition(spec.condition, spec.predicate);
    }

    // ═══════════════════════════════════════════════
    //  内置 ICondition 实现
    // ═══════════════════════════════════════════════

    private record QuestAcceptedCondition(String questId) implements ICondition {
        @Override
        public boolean test(@Nullable ServerPlayer player, Set<ResourceLocation> cq, Set<String> f, Map<String, Integer> v) {
            if (player == null) return false;
            var data = ArcQuestPlayerManager.get(player);
            if (data == null) return false;
            return data.getActiveQuest(questId) != null;
        }

        @Override
        public boolean testClient(Set<ResourceLocation> cq, Set<String> f, Map<String, Integer> v) {
            return false;
        }
    }

    private record QuestNotStartedCondition(String questId) implements ICondition {
        @Override
        public boolean test(@Nullable ServerPlayer player, Set<ResourceLocation> cq, Set<String> f, Map<String, Integer> v) {
            if (player == null) return true;
            var data = ArcQuestPlayerManager.get(player);
            if (data == null) return true;
            return data.getActiveQuest(questId) == null && !data.isQuestCompleted(questId);
        }

        @Override
        public boolean testClient(Set<ResourceLocation> cq, Set<String> f, Map<String, Integer> v) {
            return true;
        }
    }

    private record QuestPhaseCondition(String questId, String phaseId) implements ICondition {
        @Override
        public boolean test(@Nullable ServerPlayer player, Set<ResourceLocation> cq, Set<String> f, Map<String, Integer> v) {
            if (player == null) return false;
            var data = ArcQuestPlayerManager.get(player);
            if (data == null) return false;
            var qdata = data.getActiveQuest(questId);
            return qdata != null && qdata.isPhaseActive(phaseId);
        }

        @Override
        public boolean testClient(Set<ResourceLocation> cq, Set<String> f, Map<String, Integer> v) {
            return false;
        }
    }

    private record QuestPhaseCompletedCondition(String questId, String phaseId) implements ICondition {
        @Override
        public boolean test(@Nullable ServerPlayer player, Set<ResourceLocation> cq, Set<String> f, Map<String, Integer> v) {
            if (player == null) return false;
            var data = ArcQuestPlayerManager.get(player);
            if (data == null) return false;
            var qdata = data.getActiveQuest(questId);
            return qdata != null && qdata.isPhaseCompleted(phaseId);
        }

        @Override
        public boolean testClient(Set<ResourceLocation> cq, Set<String> f, Map<String, Integer> v) {
            return false;
        }
    }

    private record QuestPhaseReachedCondition(String questId, String phaseId) implements ICondition {
        @Override
        public boolean test(@Nullable ServerPlayer player, Set<ResourceLocation> cq, Set<String> f, Map<String, Integer> v) {
            if (player == null) return false;
            var data = ArcQuestPlayerManager.get(player);
            if (data == null) return false;
            var qdata = data.getActiveQuest(questId);
            return qdata != null && (qdata.isPhaseCompleted(phaseId) || qdata.isPhaseActive(phaseId));
        }

        @Override
        public boolean testClient(Set<ResourceLocation> cq, Set<String> f, Map<String, Integer> v) {
            return false;
        }
    }

    private record VanillaPredicateCondition(String predicateId, JsonObject predicate) implements ICondition {
        @Override
        public boolean test(@Nullable ServerPlayer player, Set<ResourceLocation> cq, Set<String> f, Map<String, Integer> v) {
            if (player == null) return false;
            var level = player.serverLevel();
            if (level == null) return false;
            if (predicate == null) return false;

            try {
                EntityPredicate entityPredicate = EntityPredicate.fromJson(predicate);
                if (entityPredicate == null) return false;
                return entityPredicate.matches(level, player.position(), player);
            } catch (Exception e) {
                LOGGER.warn("[ConditionBridge] Failed to evaluate vanilla entity predicate '{}': {}", predicateId, e.getMessage());
                return false;
            }
        }

        @Override
        public boolean testClient(Set<ResourceLocation> cq, Set<String> f, Map<String, Integer> v) {
            return true;
        }
    }

    private record HasEffectCondition(MobEffect effect) implements ICondition {
        @Override
        public boolean test(@Nullable ServerPlayer player, Set<ResourceLocation> cq, Set<String> f, Map<String, Integer> v) {
            return player != null && player.hasEffect(effect);
        }

        @Override
        public boolean testClient(Set<ResourceLocation> cq, Set<String> f, Map<String, Integer> v) {
            return true;
        }
    }

    private record XpLevelCondition(int threshold) implements ICondition {
        @Override
        public boolean test(@Nullable ServerPlayer player, Set<ResourceLocation> cq, Set<String> f, Map<String, Integer> v) {
            return player != null && player.experienceLevel >= threshold;
        }

        @Override
        public boolean testClient(Set<ResourceLocation> cq, Set<String> f, Map<String, Integer> v) {
            return true;
        }
    }
}