package org.arcadia.arc_quest.questplayer.snapshot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.migration.*;
import org.arcadia.arc_quest.questplayer.persistence.PlayerNbtFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FileArcQuestPlayerSnapshotStoreTest {
    @TempDir Path directory;
    private final FileArcQuestPlayerSnapshotStore store = FileArcQuestPlayerSnapshotStore.INSTANCE;
    private final UUID player = UUID.randomUUID();

    @Test
    void namesRemainUniqueWithinTheSameMillisecond() {
        var names = new HashSet<String>();
        for (int i = 0; i < 100; i++) {
            assertTrue(names.add(FileArcQuestPlayerSnapshotStore.snapshotFileName(player, 100, ArcQuestSnapshotReason.MANUAL)));
        }
    }

    @Test
    void listsBothLegacyAndUniqueNamesUsingEnvelopeMetadata() throws Exception {
        for (String name : new String[]{player + "_20260923_030000_pre_restore.dat",
                FileArcQuestPlayerSnapshotStore.snapshotFileName(player, 1234, ArcQuestSnapshotReason.PRE_RESTORE)}) {
            Path path = directory.resolve(name);
            var sections = ArcQuestPlayerMigrationSections.fromPlayerData(new ArcQuestPlayer(player).serializeNBT());
            var bundle = new ArcQuestPlayerMigrationBundle(ArcQuestPlayerMigrationBundle.FORMAT, 1,
                    new ArcQuestPlayerMigrationMeta(player, "player", 1234, 4, "test", "world", sections.getAvailableSections()), sections);
            CompoundTag encoded = new ArcQuestPlayerMigrationSerializer().serialize(bundle);
            new PlayerNbtFiles(8192, 32768).write(path, encoded);
            ArcQuestPlayerSnapshotRef ref = store.tryParseSnapshotRef(player, "world", path);
            assertNotNull(ref);
            assertEquals(1234, ref.createdAt());
            assertEquals(ArcQuestSnapshotReason.PRE_RESTORE, ref.reason());
            assertEquals(encoded, store.loadSnapshot(ref));
            assertNull(store.tryParseSnapshotRef(UUID.randomUUID(), "world", path));
        }
    }

    @Test
    void dotWorldNamesCannotEscapeTheBackupDirectory() {
        for (String value : new String[]{".", "..", " ", ""}) {
            assertEquals("unknown_world", FileArcQuestPlayerSnapshotStore.sanitizePathSegment(value));
        }
        assertFalse(FileArcQuestPlayerSnapshotStore.sanitizePathSegment("../world").contains("/"));
    }

    @Test
    void oversizedAndCorruptBackupsAreRejectedWithoutDeletingTheOriginal() throws Exception {
        Path path = directory.resolve("large.dat");
        CompoundTag root = new CompoundTag();
        root.putByteArray("payload", new byte[33 * 1024 * 1024]);
        NbtIo.writeCompressed(root, path.toFile());
        assertThrows(RuntimeException.class, () -> store.loadSnapshot(path));
        assertTrue(Files.exists(path));
        Path corrupt = directory.resolve("corrupt.dat");
        Files.writeString(corrupt, "broken gzip");
        assertThrows(IllegalStateException.class, () -> store.loadSnapshot(corrupt));
        assertTrue(Files.exists(corrupt));
    }
}
