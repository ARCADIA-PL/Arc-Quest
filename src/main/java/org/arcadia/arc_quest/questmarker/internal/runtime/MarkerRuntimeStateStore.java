package org.arcadia.arc_quest.questmarker.internal.runtime;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.questmarker.internal.MarkerIds;
import org.arcadia.arc_quest.questplayer.PlayerSessionEpochManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MarkerRuntimeStateStore {

    public static final MarkerRuntimeStateStore INSTANCE = new MarkerRuntimeStateStore();

    private final Map<UUID, PlayerState> playerStates = new ConcurrentHashMap<>();

    private MarkerRuntimeStateStore() {
    }

    public boolean shouldRefresh(ServerPlayer player, String markerId, int periodTicks, int currentTick) {
        return stateFor(player).shouldRefresh(markerId, periodTicks, currentTick);
    }

    public void setExpiry(ServerPlayer player, String markerId, int expiryTick) {
        stateFor(player).setExpiry(markerId, expiryTick);
    }

    public List<String> removeExpired(ServerPlayer player, int currentTick) {
        return stateFor(player).removeExpired(currentTick);
    }

    public void clearPlayer(UUID playerId) {
        playerStates.remove(playerId);
    }

    public void clearQuest(UUID playerId, String questId) {
        PlayerState state = playerStates.get(playerId);
        if (state == null) return;
        state.removePrefix(MarkerIds.autoQuestPrefix(questId));
        state.removePrefix(MarkerIds.triggeredQuestPrefix(questId));
    }

    public void clearAll() {
        playerStates.clear();
    }

    private PlayerState stateFor(ServerPlayer player) {
        long sessionEpoch = PlayerSessionEpochManager.getOrCreate(player);
        return playerStates.compute(player.getUUID(), (playerId, existing) ->
                existing == null || existing.sessionEpoch != sessionEpoch
                        ? new PlayerState(sessionEpoch)
                        : existing);
    }

    private static final class PlayerState {
        private final long sessionEpoch;
        private final Map<String, Integer> refreshTicks = new HashMap<>();
        private final Map<String, Integer> expiryTicks = new HashMap<>();

        private PlayerState(long sessionEpoch) {
            this.sessionEpoch = sessionEpoch;
        }

        private boolean shouldRefresh(String markerId, int period, int currentTick) {
            int normalizedPeriod = Math.max(1, period);
            Integer previous = refreshTicks.get(markerId);
            if (previous != null && currentTick >= previous && currentTick - previous < normalizedPeriod) return false;
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
