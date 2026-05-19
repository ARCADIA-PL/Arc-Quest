package org.arcadia.arc_quest.questplayer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ArcQuestPlayerManager {

    private static final ConcurrentHashMap<UUID, ArcQuestPlayer> MAP = new ConcurrentHashMap<>();

    private ArcQuestPlayerManager() {
    }

    @Nullable
    public static ArcQuestPlayer get(ServerPlayer player) {
        return MAP.get(player.getUUID());
    }

    public static ArcQuestPlayer getOrCreate(ServerPlayer player) {
        return MAP.computeIfAbsent(player.getUUID(), uuid -> {
            ArcQuestPlayer data = new ArcQuestPlayer(uuid);
            CompoundTag saved = player.getPersistentData().getCompound("ArcQuestAutosave");
            if (!saved.isEmpty()) data.deserializeNBT(saved);
            return data;
        });
    }

    public static void persistSnapshot(ServerPlayer player, ArcQuestPlayer data) {
        player.getPersistentData().put("ArcQuestAutosave", data.serializeNBT());
    }

    public static void unload(UUID uuid) {
        MAP.remove(uuid);
    }

    public static void clone(ServerPlayer from, ServerPlayer to) {
        ArcQuestPlayer old = MAP.remove(from.getUUID());
        if (old != null) {
            ArcQuestPlayer clone = new ArcQuestPlayer(to.getUUID());
            clone.deserializeNBT(old.serializeNBT());
            MAP.put(to.getUUID(), clone);
        }
    }
}
