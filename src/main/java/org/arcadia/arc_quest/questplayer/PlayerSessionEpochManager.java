package org.arcadia.arc_quest.questplayer;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;

public final class PlayerSessionEpochManager {

    private static final AtomicLong NEXT_EPOCH = new AtomicLong(System.currentTimeMillis());
    private static final ConcurrentHashMap<UUID, Long> EPOCHS = new ConcurrentHashMap<>();

    private PlayerSessionEpochManager() {
    }

    public static long beginSession(ServerPlayer player) {
        long epoch = NEXT_EPOCH.updateAndGet(Math::incrementExact);
        EPOCHS.put(player.getUUID(), epoch);
        return epoch;
    }

    public static long getOrCreate(ServerPlayer player) {
        return EPOCHS.computeIfAbsent(player.getUUID(), ignored -> NEXT_EPOCH.updateAndGet(Math::incrementExact));
    }

    public static boolean matches(ServerPlayer player, long epoch) {
        return epoch == 0L || getOrCreate(player) == epoch;
    }

    static long renew(ServerPlayer player, LongConsumer beforePublish) {
        long epoch = NEXT_EPOCH.updateAndGet(Math::incrementExact);
        beforePublish.accept(epoch);
        EPOCHS.put(player.getUUID(), epoch);
        return epoch;
    }

    public static void endSession(UUID playerId) {
        EPOCHS.remove(playerId);
    }

    public static void clear() {
        EPOCHS.clear();
    }
}
