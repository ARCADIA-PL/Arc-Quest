package org.arcadia.arc_quest.quest.network;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.questplayer.PlayerSessionEpochManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class QuestSyncRevisionManager {

    private static final Map<UUID, Cursor> CURSORS = new ConcurrentHashMap<>();

    private QuestSyncRevisionManager() {
    }

    public static synchronized Envelope next(ServerPlayer player) {
        long epoch = PlayerSessionEpochManager.getOrCreate(player);
        Cursor current = CURSORS.get(player.getUUID());
        long baseRevision = current != null && current.epoch() == epoch ? current.revision() : 0L;
        long newRevision = baseRevision + 1L;
        CURSORS.put(player.getUUID(), new Cursor(epoch, newRevision));
        return new Envelope(epoch, baseRevision, newRevision);
    }

    public static void clearPlayer(UUID playerId) {
        CURSORS.remove(playerId);
    }

    public static void clear() {
        CURSORS.clear();
    }

    public record Envelope(long playerSessionEpoch, long baseRevision, long newRevision) {
    }

    private record Cursor(long epoch, long revision) {
    }
}
