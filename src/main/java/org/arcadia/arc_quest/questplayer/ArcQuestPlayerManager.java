package org.arcadia.arc_quest.questplayer;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.core.identity.PlayerSessionRef;
import org.arcadia.arc_quest.questplayer.persistence.ArcQuestPlayerCheckpointStore;
import org.arcadia.arc_quest.questplayer.persistence.ArcQuestPlayerPersistenceMetadata;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ArcQuestPlayerManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final PlayerSessionStateStore<ArcQuestPlayer> PLAYER_STATES = new PlayerSessionStateStore<>();
    private static final ConcurrentHashMap<PlayerSessionRef, Long> PERSISTENCE_REVISIONS = new ConcurrentHashMap<>();
    private static ArcQuestPlayerRepository repository = CapabilityArcQuestPlayerRepository.INSTANCE;

    private ArcQuestPlayerManager() {
    }

    public static void setRepository(ArcQuestPlayerRepository repository) {
        ArcQuestPlayerManager.repository = repository;
    }

    @Nullable
    public static ArcQuestPlayer get(ServerPlayer player) {
        return PLAYER_STATES.get(sessionRef(player));
    }

    public static ArcQuestPlayer getOrCreate(ServerPlayer player) {
        PlayerSessionRef session = sessionRef(player);
        return PLAYER_STATES.getOrCreate(session, () -> {
            UUID uuid = session.playerUuid();
            ArcQuestPlayer data = new ArcQuestPlayer(uuid);
            CompoundTag saved = repository.loadSnapshot(player, uuid);
            CompoundTag checkpoint = ArcQuestPlayerCheckpointStore.INSTANCE.loadLatest(player, uuid);
            CompoundTag selected = ArcQuestPlayerPersistenceMetadata.newer(saved, checkpoint);
            long savedRevision = ArcQuestPlayerPersistenceMetadata.revision(saved);
            long checkpointRevision = ArcQuestPlayerPersistenceMetadata.revision(checkpoint);
            long selectedRevision = ArcQuestPlayerPersistenceMetadata.revision(selected);
            PERSISTENCE_REVISIONS.put(session, selectedRevision);
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
        PlayerSessionRef session = sessionRef(player);
        ArcQuestPlayer data = PLAYER_STATES.remove(session);
        if (data != null) {
            persist(player, data, true);
        }
        PERSISTENCE_REVISIONS.remove(session);
    }

    public static void deleteSnapshot(ServerPlayer player) {
        repository.deleteSnapshot(player, player.getUUID());
        ArcQuestPlayerCheckpointStore.INSTANCE.delete(player, player.getUUID());
        PERSISTENCE_REVISIONS.remove(sessionRef(player));
    }

    public static void unload(UUID uuid) {
        PLAYER_STATES.removePlayer(uuid);
        PERSISTENCE_REVISIONS.keySet().removeIf(session -> session.playerUuid().equals(uuid));
    }

    public static void clone(ServerPlayer from, ServerPlayer to) {
        PlayerSessionRef fromSession = sessionRef(from);
        PlayerSessionRef toSession = sessionRef(to);
        ArcQuestPlayer old = PLAYER_STATES.get(fromSession);
        if (old == null) {
            old = new ArcQuestPlayer(from.getUUID());
            CompoundTag saved = repository.loadSnapshot(from, from.getUUID());
            PERSISTENCE_REVISIONS.put(toSession, ArcQuestPlayerPersistenceMetadata.revision(saved));
            if (!saved.isEmpty()) old.deserializeNBT(saved);
        }

        ArcQuestPlayer clone = new ArcQuestPlayer(to.getUUID());
        clone.deserializeNBT(old.serializeNBT());
        PLAYER_STATES.remove(fromSession);
        Long revision = PERSISTENCE_REVISIONS.remove(fromSession);
        if (revision != null) PERSISTENCE_REVISIONS.put(toSession, revision);
        PLAYER_STATES.put(toSession, clone);
        persist(to, clone, true);
    }

    public static void clearRuntimeState() {
        PLAYER_STATES.clear();
        PERSISTENCE_REVISIONS.clear();
    }

    public static void flushCheckpoints() {
        ArcQuestPlayerCheckpointStore.INSTANCE.flush(Duration.ofSeconds(5));
    }

    private static void persist(ServerPlayer player, ArcQuestPlayer data, boolean synchronousCheckpoint) {
        UUID playerUuid = player.getUUID();
        PlayerSessionRef session = sessionRef(player);
        long revision = PERSISTENCE_REVISIONS.compute(session,
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

    private static PlayerSessionRef sessionRef(ServerPlayer player) {
        return new PlayerSessionRef(player.getUUID(), PlayerSessionEpochManager.getOrCreate(player));
    }
}
