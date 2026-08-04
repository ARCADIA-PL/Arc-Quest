package org.arcadia.arc_quest.questplayer.persistence;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ArcQuestPlayerCheckpointStore {

    public static final ArcQuestPlayerCheckpointStore INSTANCE = new ArcQuestPlayerCheckpointStore();

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FORMAT_VERSION = 1;
    private static final long MAX_COMPRESSED_BYTES = 8L * 1024L * 1024L;
    private static final long MAX_UNCOMPRESSED_BYTES = 32L * 1024L * 1024L;
    private static final long CHECKPOINT_COALESCE_SECONDS = 2L;
    private static final String DIRECTORY_NAME = "player_checkpoints";
    private static final String SNAPSHOT_KEY = "Snapshot";

    private final ScheduledExecutorService writer = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ArcQuest-PlayerCheckpoint");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<Path, PendingCheckpoint> pending = new ConcurrentHashMap<>();
    private final Set<Path> scheduledPaths = ConcurrentHashMap.newKeySet();
    private final Map<Path, Long> lastWrittenRevisions = new ConcurrentHashMap<>();
    private final Map<Path, Object> pathLocks = new ConcurrentHashMap<>();

    private ArcQuestPlayerCheckpointStore() {
    }

    public CompoundTag loadLatest(ServerPlayer player, UUID playerUuid) {
        Path path = checkpointPath(player, playerUuid);
        if (!Files.isRegularFile(path)) return new CompoundTag();

        try {
            if (Files.size(path) > MAX_COMPRESSED_BYTES) {
                quarantine(path, "oversized");
                LOGGER.error("[ArcQuestPersistence] Checkpoint exceeds compressed size limit: {}", path);
                return new CompoundTag();
            }
            CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.create(MAX_UNCOMPRESSED_BYTES));
            if (root.getInt("FormatVersion") != FORMAT_VERSION
                    || !playerUuid.toString().equals(root.getString("PlayerUuid"))
                    || !root.contains(SNAPSHOT_KEY)) {
                quarantine(path, "invalid");
                LOGGER.error("[ArcQuestPersistence] Invalid checkpoint envelope: {}", path);
                return new CompoundTag();
            }
            CompoundTag snapshot = root.getCompound(SNAPSHOT_KEY).copy();
            long envelopeRevision = Math.max(0L, root.getLong("Revision"));
            if (ArcQuestPlayerPersistenceMetadata.revision(snapshot) != envelopeRevision) {
                quarantine(path, "revision_mismatch");
                LOGGER.error("[ArcQuestPersistence] Checkpoint revision mismatch: {}", path);
                return new CompoundTag();
            }
            lastWrittenRevisions.put(path, envelopeRevision);
            return snapshot;
        } catch (Exception exception) {
            quarantine(path, "broken");
            LOGGER.error("[ArcQuestPersistence] Failed to load checkpoint: {}", path, exception);
            return new CompoundTag();
        }
    }

    public void schedule(ServerPlayer player, UUID playerUuid, CompoundTag snapshot) {
        Path path = checkpointPath(player, playerUuid);
        PendingCheckpoint checkpoint = new PendingCheckpoint(playerUuid, path, snapshot.copy());
        pending.merge(path, checkpoint, ArcQuestPlayerCheckpointStore::newer);
        schedulePath(path);
    }

    public void writeNow(ServerPlayer player, UUID playerUuid, CompoundTag snapshot) {
        Path path = checkpointPath(player, playerUuid);
        pending.remove(path);
        writeCheckpoint(new PendingCheckpoint(playerUuid, path, snapshot.copy()));
    }

    public void delete(ServerPlayer player, UUID playerUuid) {
        Path path = checkpointPath(player, playerUuid);
        pending.remove(path);
        synchronized (pathLock(path)) {
            try {
                Files.deleteIfExists(path);
                lastWrittenRevisions.remove(path);
            } catch (IOException exception) {
                LOGGER.warn("[ArcQuestPersistence] Failed to delete checkpoint: {}", path, exception);
            }
        }
    }

    public void flush(Duration timeout) {
        try {
            Future<?> barrier = writer.submit(this::flushPending);
            barrier.get(Math.max(1L, timeout.toMillis()), TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            LOGGER.warn("[ArcQuestPersistence] Timed out while flushing player checkpoints", exception);
        }
    }

    private void schedulePath(Path path) {
        if (scheduledPaths.add(path)) {
            writer.schedule(() -> drainPath(path), CHECKPOINT_COALESCE_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void drainPath(Path path) {
        try {
            PendingCheckpoint checkpoint = pending.remove(path);
            if (checkpoint != null) writeCheckpoint(checkpoint);
        } finally {
            scheduledPaths.remove(path);
            if (pending.containsKey(path)) schedulePath(path);
        }
    }

    private void flushPending() {
        while (!pending.isEmpty()) {
            for (Path path : pending.keySet()) {
                PendingCheckpoint checkpoint = pending.remove(path);
                if (checkpoint != null) writeCheckpoint(checkpoint);
            }
        }
    }

    private void writeCheckpoint(PendingCheckpoint checkpoint) {
        Path path = checkpoint.path();
        long revision = ArcQuestPlayerPersistenceMetadata.revision(checkpoint.snapshot());
        synchronized (pathLock(path)) {
            if (revision < lastWrittenRevisions.getOrDefault(path, -1L)) return;

            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            CompoundTag root = new CompoundTag();
            root.putInt("FormatVersion", FORMAT_VERSION);
            root.putString("PlayerUuid", checkpoint.playerUuid().toString());
            root.putLong("Revision", revision);
            root.putLong("WrittenAt", ArcQuestPlayerPersistenceMetadata.writtenAt(checkpoint.snapshot()));
            root.put(SNAPSHOT_KEY, checkpoint.snapshot().copy());

            try {
                Files.createDirectories(path.getParent());
                NbtIo.writeCompressed(root, temporary);
                if (Files.size(temporary) > MAX_COMPRESSED_BYTES) {
                    Files.deleteIfExists(temporary);
                    LOGGER.error("[ArcQuestPersistence] Refusing oversized checkpoint for player {}", checkpoint.playerUuid());
                    return;
                }
                try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                    channel.force(true);
                }
                atomicReplace(temporary, path);
                lastWrittenRevisions.put(path, revision);
            } catch (Exception exception) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
                LOGGER.error("[ArcQuestPersistence] Failed to write checkpoint for player {} to {}",
                        checkpoint.playerUuid(), path, exception);
            }
        }
    }

    private static PendingCheckpoint newer(PendingCheckpoint first, PendingCheckpoint second) {
        long firstRevision = ArcQuestPlayerPersistenceMetadata.revision(first.snapshot());
        long secondRevision = ArcQuestPlayerPersistenceMetadata.revision(second.snapshot());
        return secondRevision >= firstRevision ? second : first;
    }

    private static void atomicReplace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void quarantine(Path path, String reason) {
        try {
            Path broken = path.resolveSibling(path.getFileName() + "." + reason + "." + System.currentTimeMillis());
            Files.move(path, broken, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    private Object pathLock(Path path) {
        return pathLocks.computeIfAbsent(path, ignored -> new Object());
    }

    private static Path checkpointPath(ServerPlayer player, UUID playerUuid) {
        return player.server.getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve("arc_quest")
                .resolve(DIRECTORY_NAME)
                .resolve(playerUuid + ".dat");
    }

    private record PendingCheckpoint(UUID playerUuid, Path path, CompoundTag snapshot) {
    }
}
