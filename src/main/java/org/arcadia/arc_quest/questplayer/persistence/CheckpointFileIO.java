package org.arcadia.arc_quest.questplayer.persistence;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** 只接收路径、UUID 和已脱离玩家状态的 NBT；由检查点队列串行调用。 */
final class CheckpointFileIO implements CheckpointWriteQueue.Storage {
    private static final int FORMAT_VERSION = 1;
    private static final String SNAPSHOT_KEY = "Snapshot";
    private final PlayerNbtFiles files;

    CheckpointFileIO() {
        this(8L * 1024L * 1024L, 32L * 1024L * 1024L);
    }

    CheckpointFileIO(long maxCompressedBytes, long maxNbtBytes) {
        files = new PlayerNbtFiles(maxCompressedBytes, maxNbtBytes);
    }

    @Override
    public CompoundTag read(Path path, UUID playerUuid) throws IOException {
        if (!Files.isRegularFile(path)) return new CompoundTag();
        try {
            CompoundTag root = files.read(path);
            validateEnvelope(root, playerUuid);
            return root.getCompound(SNAPSHOT_KEY).copy();
        } catch (IOException | RuntimeException exception) {
            quarantine(path, exception);
            ArcQuestLog.error(ArcQuestLog.Category.PERSISTENCE,
                    "Rejected checkpoint for player {} at {}", playerUuid, path, exception);
            return new CompoundTag();
        }
    }

    @Override
    public void write(Path path, UUID playerUuid, CompoundTag snapshot) throws IOException {
        CompoundTag root = new CompoundTag();
        root.putInt("FormatVersion", FORMAT_VERSION);
        root.putString("PlayerUuid", playerUuid.toString());
        root.putLong("Revision", ArcQuestPlayerPersistenceMetadata.revision(snapshot));
        root.putLong("WrittenAt", ArcQuestPlayerPersistenceMetadata.writtenAt(snapshot));
        root.put(SNAPSHOT_KEY, snapshot);
        files.write(path, root);
    }

    @Override
    public void delete(Path path) throws IOException {
        Files.deleteIfExists(path);
    }

    private static void validateEnvelope(CompoundTag root, UUID playerUuid) throws IOException {
        if (root == null
                || !root.contains("FormatVersion", Tag.TAG_INT)
                || root.getInt("FormatVersion") != FORMAT_VERSION
                || !root.contains("PlayerUuid", Tag.TAG_STRING)
                || !playerUuid.toString().equals(root.getString("PlayerUuid"))
                || !root.contains(SNAPSHOT_KEY, Tag.TAG_COMPOUND)
                || !root.contains("Revision", Tag.TAG_LONG)
                || root.getLong("Revision") < 0L
                || !root.contains("WrittenAt", Tag.TAG_LONG)
                || root.getLong("WrittenAt") < 0L) {
            throw new IOException("Invalid checkpoint envelope");
        }
        CompoundTag snapshot = root.getCompound(SNAPSHOT_KEY);
        if (ArcQuestPlayerPersistenceMetadata.revision(snapshot) != root.getLong("Revision")
                || ArcQuestPlayerPersistenceMetadata.writtenAt(snapshot) != root.getLong("WrittenAt")) {
            throw new IOException("Checkpoint envelope and snapshot version differ");
        }
    }

    private static void quarantine(Path path, Exception cause) {
        Path rejected = path.resolveSibling(path.getFileName() + ".broken." + UUID.randomUUID());
        try {
            Files.move(path, rejected);
        } catch (IOException quarantineFailure) {
            cause.addSuppressed(quarantineFailure);
        }
    }

}
