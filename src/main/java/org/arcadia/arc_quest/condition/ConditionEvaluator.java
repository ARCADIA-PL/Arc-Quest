package org.arcadia.arc_quest.condition;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.arcadia.arc_quest.core.CoreProcessors;
import org.arcadia.arc_quest.quest.api.CompareOp;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
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

    public boolean evaluateWithEntity(ConditionSpec spec, @Nullable ServerPlayer player, @Nullable Entity entity) {
        if (spec == null || spec.isAlways()) return true;
        if ("arc_quest:entity_nbt".equals(spec.condition)) return evaluateEntityNbt(spec, entity);
        if ("arc_quest:entity_name".equals(spec.condition)) return evaluateEntityName(spec, entity);

        var playerData = player == null ? null : ArcQuestPlayerManager.get(player);
        Set<ResourceLocation> completedQuests = playerData == null
                ? Set.of()
                : playerData.getCompletedQuestLocations();
        Set<String> flags = playerData == null ? Set.of() : playerData.getAllFlags();
        Map<String, Integer> variables = playerData == null ? Map.of() : playerData.getAllVariables();
        return evaluateWithEntity(spec, player, entity, completedQuests, flags, variables);
    }

    private boolean evaluateWithEntity(ConditionSpec spec,
                                       @Nullable ServerPlayer player,
                                       @Nullable Entity entity,
                                       Set<ResourceLocation> completedQuests,
                                       Set<String> flags,
                                       Map<String, Integer> variables) {
        if (spec == null || spec.isAlways()) return true;

        String cond = spec.condition;
        if (cond == null || cond.isBlank()) return true;

        if ("arc_quest:entity_nbt".equals(cond)) {
            return evaluateEntityNbt(spec, entity);
        }
        if ("arc_quest:entity_name".equals(cond)) {
            return evaluateEntityName(spec, entity);
        }

        return switch (cond) {
            case "arc_quest:and" -> {
                if (spec.conditions == null || spec.conditions.isEmpty()) yield true;
                boolean matched = true;
                for (ConditionSpec child : spec.conditions) {
                    if (!evaluateWithEntity(child, player, entity, completedQuests, flags, variables)) {
                        matched = false;
                        break;
                    }
                }
                yield matched;
            }
            case "arc_quest:or" -> {
                if (spec.conditions == null || spec.conditions.isEmpty()) yield false;
                boolean matched = false;
                for (ConditionSpec child : spec.conditions) {
                    if (evaluateWithEntity(child, player, entity, completedQuests, flags, variables)) {
                        matched = true;
                        break;
                    }
                }
                yield matched;
            }
            case "arc_quest:not" -> spec.inner == null
                    || !evaluateWithEntity(spec.inner, player, entity, completedQuests, flags, variables);
            default -> evaluate(spec, player, completedQuests, flags, variables);
        };
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
        var data = ArcQuestPlayerManager.get(player);
        if (data == null) return false;
        return data.getActiveQuest(questId) != null;
    }

    private boolean evaluateQuestNotStarted(@Nullable ServerPlayer player, String questId) {
        if (player == null) return true;
        var data = ArcQuestPlayerManager.get(player);
        if (data == null) return true;
        return data.getActiveQuest(questId) == null && !data.isQuestCompleted(questId);
    }

    private boolean evaluateQuestPhase(@Nullable ServerPlayer player, String questId, String phaseId) {
        if (player == null) return false;
        var data = ArcQuestPlayerManager.get(player);
        if (data == null) return false;
        var qdata = data.getActiveQuest(questId);
        return qdata != null && qdata.isPhaseActive(phaseId);
    }

    private boolean evaluateQuestPhaseCompleted(@Nullable ServerPlayer player, String questId, String phaseId) {
        if (player == null) return false;
        var data = ArcQuestPlayerManager.get(player);
        if (data == null) return false;
        var qdata = data.getActiveQuest(questId);
        return qdata != null && qdata.isPhaseCompleted(phaseId);
    }

    private boolean evaluateQuestPhaseReached(@Nullable ServerPlayer player, String questId, String phaseId) {
        if (player == null) return false;
        var data = ArcQuestPlayerManager.get(player);
        if (data == null) return false;
        var qdata = data.getActiveQuest(questId);
        if (qdata == null) return false;
        return qdata.isPhaseCompleted(phaseId) || qdata.isPhaseActive(phaseId);
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
        var data = ArcQuestPlayerManager.get(player);
        if (data == null) return false;
        return data.isQuestActive(questId) || data.isQuestCompleted(questId) || data.isQuestFailed(questId);
    }

    private boolean evaluateQuestFailed(@Nullable ServerPlayer player, String questId) {
        if (player == null) return false;
        var data = ArcQuestPlayerManager.get(player);
        if (data == null) return false;
        return data.isQuestFailed(questId);
    }

    private boolean evaluatePhaseBefore(@Nullable ServerPlayer player, String questId, String targetPhaseId) {
        if (player == null) return false;
        var data = ArcQuestPlayerManager.get(player);
        if (data == null) return false;
        var qdata = data.getActiveQuest(questId);
        if (data == null) return false;
        return !qdata.isPhaseCompleted(targetPhaseId) && !qdata.isPhaseActive(targetPhaseId);
    }

    private boolean evaluatePhaseAfter(@Nullable ServerPlayer player, String questId, String targetPhaseId) {
        if (player == null) return false;
        var data = ArcQuestPlayerManager.get(player);
        if (data == null) return false;
        var qdata = data.getActiveQuest(questId);
        if (qdata == null) return false;
        return qdata.isPhaseCompleted(targetPhaseId);
    }

    private boolean evaluatePhaseBetween(@Nullable ServerPlayer player, String questId, String fromPhaseId, String toPhaseId) {
        if (player == null) return false;
        var data = ArcQuestPlayerManager.get(player);
        if (data == null) return false;
        var qdata = data.getActiveQuest(questId);
        if (qdata == null) return false;
        boolean reachedStart = qdata.isPhaseCompleted(fromPhaseId) || qdata.isPhaseActive(fromPhaseId);
        boolean reachedEnd = qdata.isPhaseCompleted(toPhaseId) || qdata.isPhaseActive(toPhaseId);
        return reachedStart && !reachedEnd;
    }

    private boolean evaluateIsMorning(@Nullable ServerPlayer player) {
        if (player == null) return false;
        return CoreProcessors.get().time().isMorning(player.level());
    }

    private boolean evaluateIsAfternoon(@Nullable ServerPlayer player) {
        if (player == null) return false;
        return CoreProcessors.get().time().isAfternoon(player.level());
    }

    private boolean evaluateIsNight(@Nullable ServerPlayer player) {
        if (player == null) return false;
        return CoreProcessors.get().time().isNight(player.level());
    }

    private boolean evaluateGameTimeInRange(@Nullable ServerPlayer player, int startTick, int endTick) {
        if (player == null) return false;
        long t = CoreProcessors.get().time().dayTime(player.level());
        if (startTick <= endTick) {
            return t >= startTick && t <= endTick;
        } else {
            return endTick == 0 ? t >= startTick : (t >= startTick || t < endTick);
        }
    }

    private boolean evaluateEntityNbt(ConditionSpec spec, @Nullable Entity entity) {
        if (entity == null || spec.nbtKey == null || spec.nbtKey.isBlank()) return false;

        CompoundTag nbt;
        if ("full".equals(spec.nbtScope)) {
            nbt = new CompoundTag();
            entity.saveWithoutId(nbt);
        } else {
            nbt = entity.getPersistentData();
        }

        if (!nbt.contains(spec.nbtKey)) return false;

        if (spec.nbtValue != null && !spec.nbtValue.isBlank()) {
            String actual = nbt.getString(spec.nbtKey);
            return spec.nbtValue.equals(actual);
        }

        return true;
    }

    private boolean evaluateEntityName(ConditionSpec spec, @Nullable Entity entity) {
        if (entity == null || spec.namePattern == null || spec.namePattern.isBlank()) return false;
        if (!entity.hasCustomName()) return false;

        String name = entity.getCustomName().getString();
        return name.contains(spec.namePattern);
    }
}
