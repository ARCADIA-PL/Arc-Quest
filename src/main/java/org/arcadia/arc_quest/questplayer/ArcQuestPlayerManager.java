package org.arcadia.arc_quest.questplayer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.core.identity.PlayerSessionRef;
import org.arcadia.arc_quest.questplayer.persistence.ArcQuestPlayerCheckpointStore;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public final class ArcQuestPlayerManager {
    private static final PlayerStateSessions SESSIONS = new PlayerStateSessions(System::currentTimeMillis);
    private static ArcQuestPlayerRepository repository = CapabilityArcQuestPlayerRepository.INSTANCE;

    private ArcQuestPlayerManager() { }

    public static void setRepository(ArcQuestPlayerRepository repository) {
        ArcQuestPlayerManager.repository = Objects.requireNonNull(repository, "repository");
    }

    @Nullable
    public static ArcQuestPlayer get(ServerPlayer player) {
        return SESSIONS.get(sessionRef(player));
    }

    public static ArcQuestPlayer getOrCreate(ServerPlayer player) {
        return SESSIONS.getOrCreate(sessionRef(player), storage(player));
    }

    public static void persistSnapshot(ServerPlayer player, ArcQuestPlayer data) {
        SESSIONS.persist(sessionRef(player), data, storage(player), false);
    }

    public static void persistAndUnload(ServerPlayer player) {
        SESSIONS.persistAndUnload(sessionRef(player), storage(player));
    }

    public static void deleteSnapshot(ServerPlayer player) {
        PlayerSessionRef session = sessionRef(player);
        repository.deleteSnapshot(player, player.getUUID());
        ArcQuestPlayerCheckpointStore.INSTANCE.deleteOrThrow(player, player.getUUID());
        SESSIONS.resetVersion(session);
    }

    public static void unload(UUID uuid) {
        SESSIONS.unload(uuid);
    }

    public static void clone(ServerPlayer from, ServerPlayer to) {
        from.getCapability(org.arcadia.arc_quest.questplayer.capability.ArcQuestCapabilities.PLAYER_DATA)
                .ifPresent(source -> to.getCapability(org.arcadia.arc_quest.questplayer.capability.ArcQuestCapabilities.PLAYER_DATA)
                        .ifPresent(target -> target.copyDeliveryReceiptsFrom(source)));
        SESSIONS.clone(sessionRef(from), sessionRef(to), storage(from), storage(to));
    }

    public static void clearRuntimeState() {
        SESSIONS.clear();
    }

    /** 使旧交互请求失效，同时保留现有玩家对象、各领域 store 和持久化版本。 */
    public static long advanceSessionEpoch(ServerPlayer player) {
        PlayerSessionRef previous = sessionRef(player);
        getOrCreate(player);
        return PlayerSessionEpochManager.renew(player,
                epoch -> SESSIONS.rebind(previous, new PlayerSessionRef(player.getUUID(), epoch)));
    }

    public static void flushCheckpoints() {
        ArcQuestPlayerCheckpointStore.INSTANCE.flush(Duration.ofSeconds(5));
    }

    private static PlayerStateSessions.Storage storage(ServerPlayer player) {
        ArcQuestPlayerRepository currentRepository = repository;
        UUID uuid = player.getUUID();
        return new PlayerStateSessions.Storage() {
            @Override
            public CompoundTag loadSaved() { return currentRepository.loadSnapshot(player, uuid); }

            @Override
            public CompoundTag loadCheckpoint() {
                return ArcQuestPlayerCheckpointStore.INSTANCE.loadLatestOrThrow(player, uuid);
            }

            @Override
            public void save(CompoundTag snapshot) { currentRepository.saveSnapshot(player, uuid, snapshot); }

            @Override
            public void checkpoint(CompoundTag snapshot, boolean synchronous) {
                if (synchronous) ArcQuestPlayerCheckpointStore.INSTANCE.writeNowOrThrow(player, uuid, snapshot);
                else ArcQuestPlayerCheckpointStore.INSTANCE.schedule(player, uuid, snapshot);
            }
        };
    }

    private static PlayerSessionRef sessionRef(ServerPlayer player) {
        if (!player.server.isSameThread()) {
            throw new IllegalStateException("Player state must be accessed on the server thread");
        }
        return new PlayerSessionRef(player.getUUID(), PlayerSessionEpochManager.getOrCreate(player));
    }
}
