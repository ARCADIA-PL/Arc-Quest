package org.arcadia.arc_quest.questplayer;

import net.minecraft.nbt.CompoundTag;
import org.arcadia.arc_quest.core.identity.PlayerSessionRef;
import org.arcadia.arc_quest.questplayer.persistence.ArcQuestPlayerPersistenceMetadata;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** 主线程封闭。每个 UUID 只保留一个当前或保存失败的状态，不持有玩家、世界或存储回调。 */
final class PlayerStateSessions {
    interface Storage {
        CompoundTag loadSaved();
        CompoundTag loadCheckpoint();
        void save(CompoundTag snapshot);
        void checkpoint(CompoundTag snapshot, boolean synchronous);
    }

    private final Map<UUID, Entry> entries = new HashMap<>();
    private final LongSupplier clock;
    private final int maxFailedExits;

    PlayerStateSessions(LongSupplier clock) {
        this(clock, 1024);
    }

    PlayerStateSessions(LongSupplier clock, int maxFailedExits) {
        if (maxFailedExits <= 0) throw new IllegalArgumentException("Recovery capacity must be positive");
        this.clock = clock;
        this.maxFailedExits = maxFailedExits;
    }

    @Nullable
    ArcQuestPlayer get(PlayerSessionRef session) {
        Entry entry = entries.get(session.playerUuid());
        return entry != null && entry.session.equals(session) ? entry.data : null;
    }

    ArcQuestPlayer getOrCreate(PlayerSessionRef session, Storage storage) {
        ArcQuestPlayer existing = get(session);
        if (existing != null) return existing;
        Entry retained = entries.get(session.playerUuid());
        requireCurrentOrNewer(session, retained);
        if (retained == null && entries.values().stream().filter(entry -> entry.failedExit).count() >= maxFailedExits) {
            // 不逐出未保存进度；存储持续故障时限制新玩家进入，已有玩家仍可恢复和退出。
            throw new IllegalStateException("Unsaved player recovery capacity reached: " + maxFailedExits);
        }

        CompoundTag saved = storage.loadSaved();
        CompoundTag checkpoint = storage.loadCheckpoint();
        CompoundTag selected = ArcQuestPlayerPersistenceMetadata.newer(saved, checkpoint);
        if (retained != null) {
            // 上次保存失败或登录前已建立状态时，保留尚未落盘的变更；同版本以内存为准。
            CompoundTag memory = snapshot(retained.data, retained.version);
            if (ArcQuestPlayerPersistenceMetadata.compare(memory, selected) >= 0) {
                if (!memory.equals(selected)) {
                    retained.version = retained.version.next(clock.getAsLong());
                    memory = snapshot(retained.data, retained.version);
                }
                selected = memory;
            }
        }

        ArcQuestPlayer data = new ArcQuestPlayer(session.playerUuid());
        if (!selected.isEmpty()) data.deserializeNBT(selected);
        Entry loaded = new Entry(session, data, PlayerPersistenceVersion.from(selected));
        boolean repairSaved = ArcQuestPlayerPersistenceMetadata.compare(selected, saved) > 0;
        boolean repairCheckpoint = ArcQuestPlayerPersistenceMetadata.compare(selected, checkpoint) > 0;
        writeSnapshot(selected, storage, repairSaved, repairCheckpoint, false);
        if (repairSaved) {
            ArcQuestLog.warn(ArcQuestLog.Category.PERSISTENCE,
                    "Recovered player {} at revision {} (saved revision {})", session.playerUuid(),
                    loaded.version.revision(), ArcQuestPlayerPersistenceMetadata.revision(saved));
        }
        entries.put(session.playerUuid(), loaded);
        return data;
    }

    void persist(PlayerSessionRef session, ArcQuestPlayer data, Storage storage, boolean synchronous) {
        if (!session.playerUuid().equals(data.getOwnerUuid())) {
            throw new IllegalArgumentException("Player snapshot owner does not match session");
        }
        getOrCreate(session, storage);
        Entry current = entries.get(session.playerUuid());
        write(current, data, storage, synchronous);
    }

    void persistAndUnload(PlayerSessionRef session, Storage storage) {
        Entry current = entries.get(session.playerUuid());
        if (current == null || !current.session.equals(session)) return;
        try {
            write(current, current.data, storage, true);
        } catch (RuntimeException failure) {
            current.failedExit = true;
            throw failure;
        }
        entries.remove(session.playerUuid(), current);
    }

    void clone(PlayerSessionRef from, PlayerSessionRef to, Storage sourceStorage, Storage targetStorage) {
        ArcQuestPlayer source = getOrCreate(from, sourceStorage);
        Entry sourceEntry = entries.get(from.playerUuid());
        Entry destination = entries.get(to.playerUuid());
        requireCurrentOrNewer(to, destination);
        PlayerPersistenceVersion version = sourceEntry.version;
        if (!from.playerUuid().equals(to.playerUuid())) {
            getOrCreate(to, targetStorage);
            destination = entries.get(to.playerUuid());
            version = newer(version, destination.version);
        }
        ArcQuestPlayer data = new ArcQuestPlayer(to.playerUuid());
        data.copyFrom(source);
        Entry replacement = new Entry(to, data, version);
        try {
            write(replacement, data, targetStorage, true);
        } finally {
            // 即使宿主写入后报错，也不能重用本次版本号覆盖可能已经成功的写入。
            if (destination != null) destination.version = newer(destination.version, replacement.version);
        }
        entries.remove(from.playerUuid(), sourceEntry);
        entries.put(to.playerUuid(), replacement);
    }

    void resetVersion(PlayerSessionRef session) {
        Entry current = entries.get(session.playerUuid());
        if (current != null && current.session.equals(session)) current.version = PlayerPersistenceVersion.INITIAL;
    }

    void rebind(PlayerSessionRef from, PlayerSessionRef to) {
        Entry current = entries.get(from.playerUuid());
        if (current == null || !current.session.equals(from)) {
            throw new IllegalStateException("Cannot rebind an inactive player session");
        }
        if (!from.playerUuid().equals(to.playerUuid()) || to.loginEpoch() <= from.loginEpoch()) {
            throw new IllegalArgumentException("Session renewal requires the same player and a newer epoch");
        }
        Entry replacement = new Entry(to, current.data, current.version);
        replacement.failedExit = current.failedExit;
        entries.put(to.playerUuid(), replacement);
    }

    void unload(UUID uuid) { entries.remove(uuid); }

    void clear() { entries.clear(); }

    int size() { return entries.size(); }

    private void write(Entry entry, ArcQuestPlayer data, Storage storage, boolean synchronous) {
        entry.version = entry.version.next(clock.getAsLong());
        CompoundTag snapshot = snapshot(data, entry.version);
        writeSnapshot(snapshot, storage, true, true, synchronous);
    }

    private static void writeSnapshot(CompoundTag snapshot, Storage storage, boolean primary,
                                      boolean checkpoint, boolean synchronous) {
        RuntimeException failure = null;
        try {
            if (primary) storage.save(snapshot.copy());
        } catch (RuntimeException exception) {
            failure = exception;
        }
        // 主存储故障时仍尝试检查点，避免两条恢复路径在同一个异常处同时失效。
        try {
            if (checkpoint) storage.checkpoint(snapshot.copy(), synchronous);
        } catch (RuntimeException exception) {
            if (failure == null) failure = exception;
            else if (failure != exception) failure.addSuppressed(exception);
        }
        if (failure != null) throw failure;
    }

    private static CompoundTag snapshot(ArcQuestPlayer data, PlayerPersistenceVersion version) {
        return ArcQuestPlayerPersistenceMetadata.stamp(data.serializeNBT(), version.revision(), version.writtenAt());
    }

    private static PlayerPersistenceVersion newer(PlayerPersistenceVersion first, PlayerPersistenceVersion second) {
        return first.revision() > second.revision()
                || first.revision() == second.revision() && first.writtenAt() >= second.writtenAt() ? first : second;
    }

    private static void requireCurrentOrNewer(PlayerSessionRef session, @Nullable Entry current) {
        if (current != null && session.loginEpoch() < current.session.loginEpoch()) {
            throw new IllegalStateException("Cannot revive stale player session: " + session);
        }
    }

    private static final class Entry {
        final PlayerSessionRef session;
        final ArcQuestPlayer data;
        PlayerPersistenceVersion version;
        boolean failedExit;

        Entry(PlayerSessionRef session, ArcQuestPlayer data, PlayerPersistenceVersion version) {
            this.session = session;
            this.data = data;
            this.version = version;
        }
    }
}
