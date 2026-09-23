package org.arcadia.arc_quest.questplayer.persistence;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CheckpointFileIOTest {
    @TempDir Path directory;
    private final UUID player = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final CheckpointFileIO storage = new CheckpointFileIO();

    @Test
    void roundTripsTheExistingEnvelopeAndSnapshot() throws Exception {
        Path path = directory.resolve("player.dat");
        CompoundTag snapshot = snapshot();
        storage.write(path, player, snapshot);
        assertEquals(snapshot, storage.read(path, player));
        CompoundTag root = NbtIo.readCompressed(path.toFile());
        assertEquals(1, root.getInt("FormatVersion"));
        assertEquals(player.toString(), root.getString("PlayerUuid"));
        assertEquals(7, root.getLong("Revision"));
        assertEquals(100, root.getLong("WrittenAt"));
        assertNoTemporaryFiles();
    }

    @Test
    void rejectsSnapshotOfTheWrongTagType() throws Exception {
        Path path = directory.resolve("player.dat");
        storage.write(path, player, snapshot());
        CompoundTag root = NbtIo.readCompressed(path.toFile());
        root.putString("Snapshot", "not a compound");
        NbtIo.writeCompressed(root, path.toFile());
        assertTrue(storage.read(path, player).isEmpty());
        assertFalse(Files.exists(path));
        try (var files = Files.list(directory)) {
            assertTrue(files.anyMatch(file -> file.getFileName().toString().contains(".broken.")));
        }
    }

    @Test
    void rejectsMismatchedPlayerOrVersionMetadata() throws Exception {
        Path wrongPlayer = directory.resolve("wrong-player.dat");
        storage.write(wrongPlayer, player, snapshot());
        assertTrue(storage.read(wrongPlayer, UUID.randomUUID()).isEmpty());
        Path wrongVersion = directory.resolve("wrong-version.dat");
        storage.write(wrongVersion, player, snapshot());
        CompoundTag root = NbtIo.readCompressed(wrongVersion.toFile());
        root.putLong("WrittenAt", 101);
        NbtIo.writeCompressed(root, wrongVersion.toFile());
        assertTrue(storage.read(wrongVersion, player).isEmpty());
    }

    @Test
    void failedCompressedWritePreservesThePreviousFileAndCleansTemporaryFiles() throws Exception {
        Path path = directory.resolve("player.dat");
        CompoundTag original = snapshot();
        storage.write(path, player, original);
        byte[] noise = new byte[8192];
        new Random(123).nextBytes(noise);
        CompoundTag replacement = snapshot();
        replacement.putByteArray("Noise", noise);
        CheckpointFileIO limited = new CheckpointFileIO(512, 64 * 1024);
        assertThrows(IOException.class, () -> limited.write(path, player, replacement));
        assertEquals(original, storage.read(path, player));
        assertNoTemporaryFiles();
    }

    @Test
    void rejectsHighlyCompressibleDataThatExceedsTheNbtBudget() throws Exception {
        Path path = directory.resolve("player.dat");
        CompoundTag original = snapshot();
        storage.write(path, player, original);
        CompoundTag replacement = snapshot();
        replacement.putByteArray("Large", new byte[8192]);
        CheckpointFileIO limited = new CheckpointFileIO(1024, 4096);
        assertThrows(IOException.class, () -> limited.write(path, player, replacement));
        assertEquals(original, storage.read(path, player));
        assertNoTemporaryFiles();
    }

    @Test
    void rejectsOversizedNbtDuringDecompressionEvenWhenTheCompressedFileIsSmall() throws Exception {
        Path path = directory.resolve("compressed-large.dat");
        CompoundTag large = snapshot();
        large.putByteArray("Large", new byte[8192]);
        storage.write(path, player, large);
        assertTrue(Files.size(path) < 1024);
        CheckpointFileIO limited = new CheckpointFileIO(1024, 4096);
        assertTrue(limited.read(path, player).isEmpty());
        assertFalse(Files.exists(path));
    }

    private static CompoundTag snapshot() {
        CompoundTag snapshot = new CompoundTag();
        snapshot.putString("TrackedQuestId", "arc_quest:test");
        return ArcQuestPlayerPersistenceMetadata.stamp(snapshot, 7, 100);
    }

    private void assertNoTemporaryFiles() throws IOException {
        try (var files = Files.list(directory)) {
            assertFalse(files.anyMatch(file -> file.getFileName().toString().endsWith(".tmp")));
        }
    }
}
