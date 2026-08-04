package org.arcadia.arc_quest.questplayer;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.questplayer.persistence.ArcQuestPlayerCheckpointStore;
import org.arcadia.arc_quest.questplayer.persistence.ArcQuestPlayerPersistenceMetadata;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ArcQuestPlayerManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ConcurrentHashMap<UUID, ArcQuestPlayer> MAP = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> PERSISTENCE_REVISIONS = new ConcurrentHashMap<>();
    private static ArcQuestPlayerRepository repository = CapabilityArcQuestPlayerRepository.INSTANCE;

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
            CompoundTag checkpoint = ArcQuestPlayerCheckpointStore.INSTANCE.loadLatest(player, uuid);
            CompoundTag selected = ArcQuestPlayerPersistenceMetadata.newer(saved, checkpoint);
            long savedRevision = ArcQuestPlayerPersistenceMetadata.revision(saved);
            long checkpointRevision = ArcQuestPlayerPersistenceMetadata.revision(checkpoint);
            PERSISTENCE_REVISIONS.put(uuid, ArcQuestPlayerPersistenceMetadata.revision(selected));
            if (!selected.isEmpty()) data.deserializeNBT(selected);
            if (checkpointRevision > savedRevision) {
                repository.saveSnapshot(player, uuid, selected);
                LOGGER.warn("[ArcQuestPersistence] Recovered player {} from crash checkpoint revision {} (saved revision {})",
                        player.getGameProfile().getName(), checkpointRevision, savedRevision);
            } else if (savedRevision > checkpointRevision) {
                ArcQuestPlayerCheckpointStore.INSTANCE.schedule(player, uuid, selected);
            }
            return data;
        });
    }

    public static void persistSnapshot(ServerPlayer player, ArcQuestPlayer data) {
        persist(player, data, false);
    }

    public static void persistAndUnload(ServerPlayer player) {
        ArcQuestPlayer data = MAP.remove(player.getUUID());
        if (data != null) persist(player, data, true);
        PERSISTENCE_REVISIONS.remove(player.getUUID());
    }

    public static void deleteSnapshot(ServerPlayer player) {
        repository.deleteSnapshot(player, player.getUUID());
        ArcQuestPlayerCheckpointStore.INSTANCE.delete(player, player.getUUID());
        PERSISTENCE_REVISIONS.remove(player.getUUID());
    }

    public static void unload(UUID uuid) {
        MAP.remove(uuid);
        PERSISTENCE_REVISIONS.remove(uuid);
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
        persist(to, clone, true);
    }

    public static void flushCheckpoints() {
        ArcQuestPlayerCheckpointStore.INSTANCE.flush(Duration.ofSeconds(5));
    }

    private static void persist(ServerPlayer player, ArcQuestPlayer data, boolean synchronousCheckpoint) {
        UUID playerUuid = player.getUUID();
        long revision = PERSISTENCE_REVISIONS.compute(playerUuid,
                (ignored, current) -> current == null || current == Long.MAX_VALUE ? 1L : current + 1L);
        CompoundTag snapshot = ArcQuestPlayerPersistenceMetadata.stamp(
                data.serializeNBT(), revision, System.currentTimeMillis());
        repository.saveSnapshot(player, playerUuid, snapshot);
        if (synchronousCheckpoint) {
            ArcQuestPlayerCheckpointStore.INSTANCE.writeNow(player, playerUuid, snapshot);
        } else {
            ArcQuestPlayerCheckpointStore.INSTANCE.schedule(player, playerUuid, snapshot);
        }
    }
}
