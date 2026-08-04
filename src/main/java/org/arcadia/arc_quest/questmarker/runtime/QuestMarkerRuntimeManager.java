package org.arcadia.arc_quest.questmarker.runtime;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.questmarker.api.MarkSpec;
import org.arcadia.arc_quest.questmarker.api.MarkTriggers;
import org.arcadia.arc_quest.questmarker.api.MarkableObject;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.PlayerSessionEpochManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class QuestMarkerRuntimeManager {
    private static final Map<UUID, PlayerRuntimeState> PLAYER_STATES = new ConcurrentHashMap<>();

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
        PlayerRuntimeState runtime = stateFor(player);
        int currentTick = player.server.getTickCount();
        if (!force && !runtime.shouldRefresh(markerId, spec.refreshTicks(), currentTick)) return false;

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
                markerId, ownerId, phaseId, objectiveIndex, spec, player, level);
        if (resolved == null) {
            if (existing != null && movingTarget) {
                data.removeMarker(markerId);
                return true;
            }
            return false;
        }
        if (resolved.equals(existing) || sameMovingBinding(existing, resolved)) return false;

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
            stateFor(player).setExpiry(markerId,
                    player.server.getTickCount() + MarkTriggers.durationTicks(spec));
        }
        return changed;
    }

    public static List<String> expire(ServerPlayer player, ArcQuestPlayer data) {
        PlayerRuntimeState state = stateFor(player);
        List<String> expired = state.removeExpired(player.server.getTickCount());
        for (String markerId : expired) data.removeMarker(markerId);
        return expired;
    }

    public static void clearPlayer(UUID playerId) {
        PLAYER_STATES.remove(playerId);
    }

    public static void clearQuest(UUID playerId, String questId) {
        PlayerRuntimeState state = PLAYER_STATES.get(playerId);
        if (state != null) {
            state.removePrefix("aq:auto:" + questId + ":");
            state.removePrefix("aq:trigger:quest:" + questId + ":");
        }
    }

    public static void clearAll() {
        PLAYER_STATES.clear();
    }

    private static PlayerRuntimeState stateFor(ServerPlayer player) {
        long sessionEpoch = PlayerSessionEpochManager.getOrCreate(player);
        return PLAYER_STATES.compute(player.getUUID(), (playerId, existing) ->
                existing == null || existing.sessionEpoch != sessionEpoch
                        ? new PlayerRuntimeState(sessionEpoch)
                        : existing);
    }

    private static boolean isEntityTarget(MarkableObject target) {
        return target instanceof MarkableObject.EntityByUuid
                || target instanceof MarkableObject.EntityByNpcId
                || target instanceof MarkableObject.EntityByTypeNearest
                || target instanceof MarkableObject.CustomResolver;
    }

    private static boolean sameMovingBinding(QuestMarkerData existing, QuestMarkerData resolved) {
        if (existing == null || !existing.hasEntityBinding() || !resolved.hasEntityBinding()) return false;
        return existing.getFollowEntityId() == resolved.getFollowEntityId()
                && existing.getFollowEntityUuid().equals(resolved.getFollowEntityUuid())
                && existing.getFollowEntityGuid().equals(resolved.getFollowEntityGuid())
                && existing.getAttachPoint() == resolved.getAttachPoint()
                && existing.getQuestId().equals(resolved.getQuestId())
                && existing.getPhaseId().equals(resolved.getPhaseId())
                && existing.getObjectiveIndex() == resolved.getObjectiveIndex()
                && existing.getType() == resolved.getType()
                && existing.getState() == resolved.getState()
                && existing.getColorARGB() == resolved.getColorARGB()
                && existing.isShowDistance() == resolved.isShowDistance()
                && existing.isAllowOffscreenArrow() == resolved.isAllowOffscreenArrow()
                && existing.getLabel().equals(resolved.getLabel())
                && existing.getPriority() == resolved.getPriority()
                && existing.getStyleHints().equals(resolved.getStyleHints());
    }

    private static final class PlayerRuntimeState {
        private final long sessionEpoch;
        private final Map<String, Integer> refreshTicks = new HashMap<>();
        private final Map<String, Integer> expiryTicks = new HashMap<>();

        private PlayerRuntimeState(long sessionEpoch) {
            this.sessionEpoch = sessionEpoch;
        }

        private boolean shouldRefresh(String markerId, int period, int currentTick) {
            int normalizedPeriod = Math.max(1, period);
            Integer previous = refreshTicks.get(markerId);
            if (previous != null && currentTick >= previous
                    && currentTick - previous < normalizedPeriod) return false;
            refreshTicks.put(markerId, currentTick);
            return true;
        }

        private void removePrefix(String prefix) {
            refreshTicks.keySet().removeIf(id -> id.startsWith(prefix));
            expiryTicks.keySet().removeIf(id -> id.startsWith(prefix));
        }

        private void setExpiry(String markerId, int expiryTick) {
            expiryTicks.put(markerId, expiryTick);
        }

        private List<String> removeExpired(int currentTick) {
            List<String> expired = new ArrayList<>();
            expiryTicks.entrySet().removeIf(entry -> {
                if (currentTick < entry.getValue()) return false;
                expired.add(entry.getKey());
                refreshTicks.remove(entry.getKey());
                return true;
            });
            return expired;
        }
    }
}
