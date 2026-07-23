package org.arcadia.arc_quest.questplayer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ArcQuestPlayerManager {

    private static final ConcurrentHashMap<UUID, ArcQuestPlayer> MAP = new ConcurrentHashMap<>();
    private static ArcQuestPlayerRepository repository = SavedDataArcQuestPlayerRepository.INSTANCE;

    private ArcQuestPlayerManager() {
    }

    public static void setRepository(ArcQuestPlayerRepository repository) {
        ArcQuestPlayerManager.repository = repository;
    }

    @Nullable
    public static ArcQuestPlayer get(ServerPlayer player) {
        return MAP.get(player.getUUID());
    }

    public static ArcQuestPlayer getOrCreate(ServerPlayer player) {
        return MAP.computeIfAbsent(player.getUUID(), uuid -> {
            ArcQuestPlayer data = new ArcQuestPlayer(uuid);
            CompoundTag saved = repository.loadSnapshot(player, uuid);
            if (!saved.isEmpty()) data.deserializeNBT(saved);
            return data;
        });
    }

    public static void persistSnapshot(ServerPlayer player, ArcQuestPlayer data) {
        repository.saveSnapshot(player, player.getUUID(), data.serializeNBT());
    }

    public static void persistAndUnload(ServerPlayer player) {
        ArcQuestPlayer data = MAP.remove(player.getUUID());
        if (data != null) {
            repository.saveSnapshot(player, player.getUUID(), data.serializeNBT());
        }
    }

    public static void deleteSnapshot(ServerPlayer player) {
        repository.deleteSnapshot(player, player.getUUID());
    }

    public static void unload(UUID uuid) {
        MAP.remove(uuid);
    }

    public static void clone(ServerPlayer from, ServerPlayer to) {
        ArcQuestPlayer old = MAP.get(from.getUUID());
        if (old == null) {
            old = new ArcQuestPlayer(from.getUUID());
            CompoundTag saved = repository.loadSnapshot(from, from.getUUID());
            if (!saved.isEmpty()) old.deserializeNBT(saved);
        }

        ArcQuestPlayer clone = new ArcQuestPlayer(to.getUUID());
        clone.deserializeNBT(old.serializeNBT());
        MAP.remove(from.getUUID());
        MAP.put(to.getUUID(), clone);
        repository.saveSnapshot(to, to.getUUID(), clone.serializeNBT());
    }
}
