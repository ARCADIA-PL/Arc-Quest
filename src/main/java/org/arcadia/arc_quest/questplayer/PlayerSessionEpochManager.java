package org.arcadia.arc_quest.questplayer;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class PlayerSessionEpochManager {

    private static final AtomicLong NEXT_EPOCH = new AtomicLong(System.currentTimeMillis());
    private static final ConcurrentHashMap<UUID, Long> EPOCHS = new ConcurrentHashMap<>();

    private PlayerSessionEpochManager() {
    }

    public static long beginSession(ServerPlayer player) {
        long epoch = NEXT_EPOCH.incrementAndGet();
        EPOCHS.put(player.getUUID(), epoch);
        return epoch;
    }

    public static long getOrCreate(ServerPlayer player) {
        return EPOCHS.computeIfAbsent(player.getUUID(), ignored -> NEXT_EPOCH.incrementAndGet());
    }

    public static boolean matches(ServerPlayer player, long epoch) {
        return epoch == 0L || getOrCreate(player) == epoch;
    }

    public static void endSession(UUID playerId) {
        EPOCHS.remove(playerId);
    }

    public static void clear() {
        EPOCHS.clear();
    }
}
