package org.arcadia.arc_quest.condition;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.arcadia.arc_quest.quest.api.CompareOp;
import org.arcadia.arc_quest.quest.capability.QuestCapabilityProvider;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 统一条件评估引擎。
 * <p>
 * 支持 Arc Quest 内置条件 + 原版 predicate 委托。
 * 服务端完整评估，客户端跳过原版 predicate。
 */
public final class ConditionEvaluator {

    private static final Logger LOGGER = LogUtils.getLogger();

    public boolean evaluate(ConditionSpec spec,
                            @Nullable ServerPlayer player,
                            Set<ResourceLocation> completedQuests,
                            Set<String> flags,
                            Map<String, Integer> variables) {
        if (spec == null || spec.isAlways()) return true;

        String cond = spec.condition;
        if (cond == null || cond.isBlank()) return true;

        if (cond.startsWith("minecraft:")) {
            return evaluateVanillaPredicate(spec, player);
        }

        return switch (cond) {
            case "arc_quest:always" -> true;
            case "arc_quest:quest_completed" -> completedQuests.contains(ResourceLocation.parse(spec.questId));
            case "arc_quest:quest_accepted" -> evaluateQuestAccepted(player, spec.questId);
            case "arc_quest:quest_not_started" -> evaluateQuestNotStarted(player, spec.questId);
            case "arc_quest:has_quest" -> evaluateHasQuest(player, spec.questId);
            case "arc_quest:quest_failed" -> evaluateQuestFailed(player, spec.questId);
            case "arc_quest:quest_phase" -> evaluateQuestPhase(player, spec.questId, spec.phaseId);
            case "arc_quest:quest_phase_completed" -> evaluateQuestPhaseCompleted(player, spec.questId, spec.phaseId);
            case "arc_quest:quest_phase_reached" -> evaluateQuestPhaseReached(player, spec.questId, spec.phaseId);
            case "arc_quest:phase_before" -> evaluatePhaseBefore(player, spec.questId, spec.targetPhaseId);
            case "arc_quest:phase_after" -> evaluatePhaseAfter(player, spec.questId, spec.targetPhaseId);
            case "arc_quest:phase_between" -> evaluatePhaseBetween(player, spec.questId, spec.fromPhaseId, spec.toPhaseId);
            case "arc_quest:has_flag" -> flags.contains(spec.flag);
            case "arc_quest:not_has_flag" -> !flags.contains(spec.flag);
            case "arc_quest:variable_check" -> evaluateVariable(variables, spec.key, spec.op, spec.value);
            case "arc_quest:is_morning" -> evaluateIsMorning(player);
            case "arc_quest:is_afternoon" -> evaluateIsAfternoon(player);
            case "arc_quest:is_night" -> evaluateIsNight(player);
            case "arc_quest:game_time_in_range" -> evaluateGameTimeInRange(player, spec.startTick, spec.endTick);
            case "arc_quest:and" -> evaluateAnd(spec, player, completedQuests, flags, variables);
            case "arc_quest:or" -> evaluateOr(spec, player, completedQuests, flags, variables);
            case "arc_quest:not" -> evaluateNot(spec, player, completedQuests, flags, variables);
            default -> {
                LOGGER.warn("[ConditionEvaluator] Unknown condition type: {}", cond);
                yield false;
            }
        };
    }

    public boolean evaluateClient(ConditionSpec spec,
                                  Set<ResourceLocation> completedQuests,
                                  Set<String> flags,
                                  Map<String, Integer> variables) {
        return evaluate(spec, null, completedQuests, flags, variables);
    }

    public boolean evaluate(ConditionSpec spec, @Nullable ServerPlayer player) {
        return evaluate(spec, player, Set.of(), Set.of(), Map.of());
    }

    private boolean evaluateVanillaPredicate(ConditionSpec spec, @Nullable ServerPlayer player) {
        if (player == null) return true;

        ServerLevel level = player.serverLevel();
        if (level == null) return false;

        JsonObject predicateJson = spec.predicate;
        if (predicateJson == null) return false;

        try {
            EntityPredicate entityPredicate = EntityPredicate.fromJson(predicateJson);
            if (entityPredicate == null) return false;
            return entityPredicate.matches(level, player.position(), player);
        } catch (Exception e) {
            LOGGER.warn("[ConditionEvaluator] Failed to evaluate vanilla entity predicate: {}", e.getMessage());
            return false;
        }
    }

    private boolean evaluateQuestAccepted(@Nullable ServerPlayer player, String questId) {
        if (player == null) return false;
        var cap = QuestCapabilityProvider.getOrNull(player);
        if (cap == null) return false;
        return cap.getActiveQuest(questId) != null;
    }

    private boolean evaluateQuestNotStarted(@Nullable ServerPlayer player, String questId) {
        if (player == null) return true;
        var cap = QuestCapabilityProvider.getOrNull(player);
        if (cap == null) return true;
        return cap.getActiveQuest(questId) == null && !cap.isQuestCompleted(questId);
    }

    private boolean evaluateQuestPhase(@Nullable ServerPlayer player, String questId, String phaseId) {
        if (player == null) return false;
        var cap = QuestCapabilityProvider.getOrNull(player);
        if (cap == null) return false;
        var data = cap.getActiveQuest(questId);
        return data != null && data.isPhaseActive(phaseId);
    }

    private boolean evaluateQuestPhaseCompleted(@Nullable ServerPlayer player, String questId, String phaseId) {
        if (player == null) return false;
        var cap = QuestCapabilityProvider.getOrNull(player);
        if (cap == null) return false;
        var data = cap.getActiveQuest(questId);
        return data != null && data.isPhaseCompleted(phaseId);
    }

    private boolean evaluateQuestPhaseReached(@Nullable ServerPlayer player, String questId, String phaseId) {
        if (player == null) return false;
        var cap = QuestCapabilityProvider.getOrNull(player);
        if (cap == null) return false;
        var data = cap.getActiveQuest(questId);
        if (data == null) return false;
        return data.isPhaseCompleted(phaseId) || data.isPhaseActive(phaseId);
    }

    private boolean evaluateVariable(Map<String, Integer> variables, String key, String op, int value) {
        int actual = variables.getOrDefault(key, 0);
        return CompareOp.fromSymbol(op).evaluate(actual, value);
    }

    private boolean evaluateAnd(ConditionSpec spec,
                                @Nullable ServerPlayer player,
                                Set<ResourceLocation> completedQuests,
                                Set<String> flags,
                                Map<String, Integer> variables) {
        if (spec.conditions == null || spec.conditions.isEmpty()) return true;
        for (ConditionSpec sub : spec.conditions) {
            if (!evaluate(sub, player, completedQuests, flags, variables)) return false;
        }
        return true;
    }

    private boolean evaluateOr(ConditionSpec spec,
                               @Nullable ServerPlayer player,
                               Set<ResourceLocation> completedQuests,
                               Set<String> flags,
                               Map<String, Integer> variables) {
        if (spec.conditions == null || spec.conditions.isEmpty()) return false;
        for (ConditionSpec sub : spec.conditions) {
            if (evaluate(sub, player, completedQuests, flags, variables)) return true;
        }
        return false;
    }

    private boolean evaluateNot(ConditionSpec spec,
                                @Nullable ServerPlayer player,
                                Set<ResourceLocation> completedQuests,
                                Set<String> flags,
                                Map<String, Integer> variables) {
        if (spec.inner == null) return true;
        return !evaluate(spec.inner, player, completedQuests, flags, variables);
    }

    private boolean evaluateHasQuest(@Nullable ServerPlayer player, String questId) {
        if (player == null) return false;
        var cap = QuestCapabilityProvider.getOrNull(player);
        if (cap == null) return false;
        return cap.isQuestActive(questId) || cap.isQuestCompleted(questId) || cap.isQuestFailed(questId);
    }

    private boolean evaluateQuestFailed(@Nullable ServerPlayer player, String questId) {
        if (player == null) return false;
        var cap = QuestCapabilityProvider.getOrNull(player);
        if (cap == null) return false;
        return cap.isQuestFailed(questId);
    }

    private boolean evaluatePhaseBefore(@Nullable ServerPlayer player, String questId, String targetPhaseId) {
        if (player == null) return false;
        var cap = QuestCapabilityProvider.getOrNull(player);
        if (cap == null) return false;
        var data = cap.getActiveQuest(questId);
        if (data == null) return false;
        return !data.isPhaseCompleted(targetPhaseId) && !data.isPhaseActive(targetPhaseId);
    }

    private boolean evaluatePhaseAfter(@Nullable ServerPlayer player, String questId, String targetPhaseId) {
        if (player == null) return false;
        var cap = QuestCapabilityProvider.getOrNull(player);
        if (cap == null) return false;
        var data = cap.getActiveQuest(questId);
        if (data == null) return false;
        return data.isPhaseCompleted(targetPhaseId);
    }

    private boolean evaluatePhaseBetween(@Nullable ServerPlayer player, String questId, String fromPhaseId, String toPhaseId) {
        if (player == null) return false;
        var cap = QuestCapabilityProvider.getOrNull(player);
        if (cap == null) return false;
        var data = cap.getActiveQuest(questId);
        if (data == null) return false;
        boolean reachedStart = data.isPhaseCompleted(fromPhaseId) || data.isPhaseActive(fromPhaseId);
        boolean reachedEnd = data.isPhaseCompleted(toPhaseId) || data.isPhaseActive(toPhaseId);
        return reachedStart && !reachedEnd;
    }

    private boolean evaluateIsMorning(@Nullable ServerPlayer player) {
        if (player == null) return false;
        long t = player.level().getDayTime() % 24000;
        return t >= 0 && t < 6000;
    }

    private boolean evaluateIsAfternoon(@Nullable ServerPlayer player) {
        if (player == null) return false;
        long t = player.level().getDayTime() % 24000;
        return t >= 6000 && t < 12000;
    }

    private boolean evaluateIsNight(@Nullable ServerPlayer player) {
        if (player == null) return false;
        long t = player.level().getDayTime() % 24000;
        return t >= 12000 && t < 24000;
    }

    private boolean evaluateGameTimeInRange(@Nullable ServerPlayer player, int startTick, int endTick) {
        if (player == null) return false;
        long t = player.level().getDayTime() % 24000;
        if (startTick <= endTick) {
            return t >= startTick && t <= endTick;
        } else {
            return endTick == 0 ? t >= startTick : (t >= startTick || t < endTick);
        }
    }
}