package org.arcadia.arc_quest.quest.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;
import org.arcadia.arc_quest.api.event.quest.QuestTrackerRebuiltEvent;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.tracking.ObjectiveKey;
import org.arcadia.arc_quest.quest.tracking.ObjectiveTracker;
import org.arcadia.arc_quest.quest.tracking.TrackedObjective;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

/**
 * 目标索引与动态目标数量解析；不改变任务或阶段的生命周期。
 */
final class QuestObjectiveService {
    private QuestObjectiveService() {
    }

    static void rebuildTrackingIndex(ServerPlayer player, ArcQuestPlayer data) {
        ObjectiveTracker.INSTANCE.unregisterPlayer(player.getUUID());

        int activeQuestCount = 0;
        for (Map.Entry<String, QuestRuntimeData> entry : data.getAllActiveQuests().entrySet()) {
            QuestRuntimeData qdata = entry.getValue();
            if (qdata.getState() != QuestState.ACTIVE) continue;
            activeQuestCount++;

            QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(qdata.getQuestId()));
            if (def == null) continue;

            for (String phaseId : qdata.getActivePhaseIds()) {
                PhaseDefinition phase = def.getPhase(phaseId);
                if (phase == null) continue;
                registerPhaseObjectives(player, def, phase);
            }

            QuestMarkerService.refreshQuestMarkers(player, data, qdata, def);
        }

        MinecraftForge.EVENT_BUS.post(new QuestTrackerRebuiltEvent(player, activeQuestCount));
    }

    static void registerPhaseObjectives(ServerPlayer player,
            QuestDefinition def,
            PhaseDefinition phase) {
        List<ObjectiveEntry> objectives = phase.getObjectives();
        for (int i = 0; i < objectives.size(); i++) {
            ObjectiveEntry obj = objectives.get(i);
            List<ResourceLocation> keys = objectiveKeyTargets(obj);
            for (ResourceLocation keyTarget : keys) {
                TrackedObjective tracked = new TrackedObjective(
                        player.getUUID(),
                        def.getId(),
                        phase.getPhaseId(),
                        i,
                        new ObjectiveKey(obj.getType(), keyTarget),
                        obj.getRequiredCount()
                );
                ObjectiveTracker.INSTANCE.register(tracked);
            }
        }
    }

    static void unregisterPhaseObjectives(ServerPlayer player,
            QuestDefinition def,
            PhaseDefinition phase) {
        List<ObjectiveEntry> objectives = phase.getObjectives();
        for (int i = 0; i < objectives.size(); i++) {
            ObjectiveEntry obj = objectives.get(i);
            List<ResourceLocation> keys = objectiveKeyTargets(obj);
            for (ResourceLocation keyTarget : keys) {
                TrackedObjective tracked = new TrackedObjective(
                        player.getUUID(),
                        def.getId(),
                        phase.getPhaseId(),
                        i,
                        new ObjectiveKey(obj.getType(), keyTarget),
                        obj.getRequiredCount()
                );
                ObjectiveTracker.INSTANCE.unregister(tracked);
            }
        }
    }

    static int resolveRequiredCount(ServerPlayer player, ObjectiveEntry obj, ArcQuestPlayer data) {
        int fromModifier = obj.resolveRequiredCount(player);

        String modeRaw = obj.getExtra("count_mode");
        String mode = modeRaw == null ? "" : modeRaw.trim().toLowerCase(Locale.ROOT);
        if (mode.isEmpty()) return Math.max(1, fromModifier);

        int base = obj.getExtraInt("count_base", fromModifier);
        int min = obj.getExtraInt("count_min", 1);
        int max = obj.getExtraInt("count_max", -1);

        int variableValue = 0;
        if ("variable".equals(mode)) {
            String var = obj.getExtra("count_var");
            variableValue = (var == null || var.isEmpty()) ? 0 : data.getVariable(var);
        }

        return computeRequiredCount(modeRaw, mode, fromModifier, base, min, max, player.experienceLevel, obj.getExtraInt("count_per_level", 0), variableValue, obj.getExtraInt("count_per_var", 0), obj.getTargetId().toString());
    }

    static int computeRequiredCount(String modeRaw,
            String normalizedMode,
            int fallbackRequired,
            int base,
            int min,
            int max,
            int playerLevel,
            int countPerLevel,
            int variableValue,
            int countPerVar,
            String objectiveDebugId) {
        boolean knownMode = switch (normalizedMode) {
            case "player_level", "level_scale", "variable", "fixed" -> true;
            default -> false;
        };
        if (!knownMode) {
            ArcQuestLog.warn(ArcQuestLog.Category.QUEST_PROGRESS,
                    "Unknown count_mode '{}' for objective {}, fallback to requiredCount", modeRaw, objectiveDebugId);
        }
        return QuestProgressRules.requiredCount(normalizedMode, fallbackRequired, base, min, max,
                playerLevel, countPerLevel, variableValue, countPerVar);
    }

    static List<ResourceLocation> objectiveKeyTargets(ObjectiveEntry obj) {
        String tag = obj.getExtra("target_tag");
        if (tag == null || tag.isEmpty()) {
            return List.of(obj.getTargetId());
        }

        ResourceLocation tagId = ResourceLocation.parse(tag);
        TagKey<Item> key = TagKey.create(Registries.ITEM, tagId);
        var named = ForgeRegistries.ITEMS.tags();
        if (named == null) return List.of(obj.getTargetId());

        List<ResourceLocation> ids = new ArrayList<>();
        for (Item taggedItem : named.getTag(key)) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(taggedItem);
            if (id != null) ids.add(id);
        }
        if (ids.isEmpty()) ids.add(obj.getTargetId());
        return ids;
    }
}
