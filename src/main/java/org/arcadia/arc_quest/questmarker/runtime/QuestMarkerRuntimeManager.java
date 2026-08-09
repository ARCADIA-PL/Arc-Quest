package org.arcadia.arc_quest.questmarker.runtime;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.questmarker.api.MarkSpec;
import org.arcadia.arc_quest.questmarker.api.MarkTriggers;
import org.arcadia.arc_quest.questmarker.api.MarkableObject;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questmarker.internal.MarkerModelAdapter;
import org.arcadia.arc_quest.questmarker.internal.runtime.MarkerRuntimeStateStore;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;

import java.util.List;
import java.util.UUID;

public final class QuestMarkerRuntimeManager {
    private static final MarkerRuntimeStateStore RUNTIME_STATES = MarkerRuntimeStateStore.INSTANCE;

    private QuestMarkerRuntimeManager() {
    }

    public static boolean refresh(ServerPlayer player,
                                  ArcQuestPlayer data,
                                  String markerId,
                                  String ownerId,
                                  MarkSpec spec,
                                  String phaseId,
                                  int objectiveIndex,
                                  boolean force) {
        int currentTick = player.server.getTickCount();
        if (!force && !RUNTIME_STATES.shouldRefresh(player, markerId, spec.refreshTicks(), currentTick)) return false;

        QuestMarkerData existing = data.getAllMarkers().get(markerId);
        boolean active = spec.activateWhen().test(player, data) && !spec.deactivateWhen().test(player, data);
        if (!active) {
            if (existing == null) return false;
            data.removeMarker(markerId);
            return true;
        }

        if (spec.oneShot() && data.isOneShotMarkerConsumed(markerId) && existing == null) return false;
        boolean movingTarget = spec.trackMovingEntity() && isEntityTarget(spec.target());
        if (existing != null && !movingTarget) return false;

        ServerLevel level = player.serverLevel();
        QuestMarkerData resolved = QuestMarkerTargetService.resolve(
                markerId, ownerId, phaseId, objectiveIndex, spec, player, level, existing);
        if (resolved == null) {
            if (existing != null && movingTarget) {
                data.removeMarker(markerId);
                return true;
            }
            return false;
        }
        if (resolved.equals(existing) || MarkerModelAdapter.sameMovingBinding(existing, resolved)) return false;

        data.upsertMarker(resolved);
        if (spec.oneShot()) data.consumeOneShotMarker(markerId);
        return true;
    }

    public static boolean trigger(ServerPlayer player,
                                  ArcQuestPlayer data,
                                  String markerId,
                                  String ownerId,
                                  MarkSpec spec,
                                  String phaseId,
                                  int objectiveIndex) {
        boolean changed = refresh(player, data, markerId, ownerId, spec, phaseId, objectiveIndex, true);
        QuestMarkerData marker = data.getAllMarkers().get(markerId);
        if (marker != null) {
            RUNTIME_STATES.setExpiry(player, markerId,
                    player.server.getTickCount() + MarkTriggers.durationTicks(spec));
        }
        return changed;
    }

    public static List<String> expire(ServerPlayer player, ArcQuestPlayer data) {
        List<String> expired = RUNTIME_STATES.removeExpired(player, player.server.getTickCount());
        for (String markerId : expired) data.removeMarker(markerId);
        return expired;
    }

    public static void clearPlayer(UUID playerId) {
        RUNTIME_STATES.clearPlayer(playerId);
    }

    public static void clearQuest(UUID playerId, String questId) {
        RUNTIME_STATES.clearQuest(playerId, questId);
    }

    public static void clearAll() {
        RUNTIME_STATES.clearAll();
    }

    private static boolean isEntityTarget(MarkableObject target) {
        return target instanceof MarkableObject.EntityByUuid
                || target instanceof MarkableObject.EntityByNpcId
                || target instanceof MarkableObject.EntityByTypeNearest
                || target instanceof MarkableObject.EntityByTypeThenStructure
                || target instanceof MarkableObject.CustomResolver;
    }

}
