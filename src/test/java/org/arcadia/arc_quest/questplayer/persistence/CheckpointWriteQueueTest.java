package org.arcadia.arc_quest.questplayer.persistence;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CheckpointWriteQueueTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    @TempDir Path directory;
    private final List<CheckpointWriteQueue> queues = new ArrayList<>();
    private final List<MemoryStorage> storages = new ArrayList<>();

    @AfterEach
    void closeWriters() throws Exception {
        for (MemoryStorage storage : storages) {
            storage.failAllWrites = false;
            storage.release.countDown();
        }
        for (CheckpointWriteQueue queue : queues) queue.shutdown(Duration.ofSeconds(5));
    }

    @Test
    void coalescesUpdatesAndDetachesMutableNbt() throws Exception {
        MemoryStorage storage = storage();
        CheckpointWriteQueue queue = queue(storage, Duration.ofDays(1));
        Path path = path("player");
        CompoundTag latest = snapshot(3, 10, "latest");
        queue.schedule(path, PLAYER, snapshot(1, 100, "old"));
        queue.schedule(path, PLAYER, latest);
        queue.schedule(path, PLAYER, snapshot(2, 1000, "stale"));
        latest.putString("Value", "changed after scheduling");
        await(queue.flush());
        assertEquals(1, storage.writes.get());
        assertEquals("latest", storage.files.get(path).getString("Value"));
    }

    @Test
    void readIncludesQueuedDataWithoutLosingTheScheduledWrite() throws Exception {
        MemoryStorage storage = storage();
        CheckpointWriteQueue queue = queue(storage, Duration.ofDays(1));
        Path path = path("player");
        storage.files.put(path, snapshot(1, 10, "disk"));
        queue.schedule(path, PLAYER, snapshot(2, 10, "queued"));
        CompoundTag loaded = await(queue.read(path, PLAYER));
        assertEquals("queued", loaded.getString("Value"));
        loaded.putString("Value", "modified reader result");
        assertEquals(0, storage.writes.get());
        await(queue.flush());
        assertEquals("queued", storage.files.get(path).getString("Value"));
    }

    @Test
    void immediateWriteDoesNotDiscardANewerQueuedSnapshot() throws Exception {
        MemoryStorage storage = storage();
        CheckpointWriteQueue queue = queue(storage, Duration.ofDays(1));
        Path path = path("player");
        queue.schedule(path, PLAYER, snapshot(7, 20, "queued"));
        await(queue.writeNow(path, PLAYER, snapshot(6, 100, "old immediate")));
        await(queue.flush());
        assertEquals("queued", storage.files.get(path).getString("Value"));
        assertEquals(1, storage.writes.get());
    }

    @Test
    void equalRevisionsUseWrittenTimeAcrossWriterRestart() throws Exception {
        MemoryStorage storage = storage();
        CheckpointWriteQueue queue = queue(storage, Duration.ofDays(1));
        Path path = path("player");
        await(queue.writeNow(path, PLAYER, snapshot(7, 100, "newer")));
        queue.shutdown(Duration.ofSeconds(5));
        assertFalse(queue.isRunning());
        await(queue.writeNow(path, PLAYER, snapshot(7, 90, "older")));
        assertEquals("newer", storage.files.get(path).getString("Value"));
        assertEquals(1, storage.writes.get());
    }

    @Test
    void deleteCancelsPendingWritesAndAllowsANewHistory() throws Exception {
        MemoryStorage storage = storage();
        CheckpointWriteQueue queue = queue(storage, Duration.ofDays(1));
        Path path = path("player");
        await(queue.writeNow(path, PLAYER, snapshot(99, 100, "previous")));
        queue.schedule(path, PLAYER, snapshot(100, 100, "pending"));
        await(queue.delete(path));
        await(queue.flush());
        assertFalse(storage.files.containsKey(path));
        queue.schedule(path, PLAYER, snapshot(1, 1, "reset"));
        await(queue.flush());
        assertEquals("reset", storage.files.get(path).getString("Value"));
    }

    @Test
    void deleteAfterAnInFlightWriteCannotResurrectTheFile() throws Exception {
        verifyDeleteDuringWrite(false);
    }

    @Test
    void deleteDuringAFailedWriteAlsoCancelsItsRetry() throws Exception {
        verifyDeleteDuringWrite(true);
    }

    @Test
    void flushIncludesARetryCreatedWhileTheBarrierWasWaiting() throws Exception {
        MemoryStorage storage = storage();
        storage.blockFirstWrite = true;
        storage.failFirstWrite = true;
        CheckpointWriteQueue queue = queue(storage, Duration.ZERO);
        Path path = path("player");
        queue.schedule(path, PLAYER, snapshot(1, 1, "recover"));
        assertTrue(storage.started.await(5, TimeUnit.SECONDS));
        Future<?> barrier = queue.flush();
        storage.release.countDown();
        await(barrier);
        assertEquals("recover", storage.files.get(path).getString("Value"));
        assertEquals(2, storage.writes.get());
    }

    @Test
    void retryCannotReplaceALaterSnapshotWithTheSameVersion() throws Exception {
        MemoryStorage storage = storage();
        storage.blockFirstWrite = true;
        storage.failFirstWrite = true;
        CheckpointWriteQueue queue = queue(storage, Duration.ZERO);
        Path path = path("player");
        queue.schedule(path, PLAYER, snapshot(1, 1, "first"));
        assertTrue(storage.started.await(5, TimeUnit.SECONDS));
        queue.schedule(path, PLAYER, snapshot(1, 1, "later"));
        Future<?> barrier = queue.flush();
        storage.release.countDown();
        await(barrier);
        assertEquals("later", storage.files.get(path).getString("Value"));
    }

    @Test
    void queueLimitsRetainAcceptedDataAndReleaseCapacityAfterFlush() throws Exception {
        MemoryStorage storage = storage();
        CheckpointWriteQueue queue = new CheckpointWriteQueue(storage, Duration.ofDays(1), 1, 4096);
        queues.add(queue);
        queue.schedule(path("first"), PLAYER, snapshot(1, 1, "accepted"));
        assertThrows(RejectedExecutionException.class,
                () -> queue.schedule(path("second"), PLAYER, snapshot(1, 1, "rejected")));
        CompoundTag tooLarge = snapshot(2, 2, "x".repeat(4096));
        assertThrows(RejectedExecutionException.class, () -> queue.schedule(path("first"), PLAYER, tooLarge));
        await(queue.flush());
        assertEquals("accepted", storage.files.get(path("first")).getString("Value"));
        queue.schedule(path("second"), PLAYER, snapshot(1, 1, "now accepted"));
        await(queue.flush());
        assertEquals("now accepted", storage.files.get(path("second")).getString("Value"));
    }

    @Test
    void flushReportsIoFailureAndStillWritesOtherPlayers() throws Exception {
        MemoryStorage storage = storage();
        storage.failingPath = path("broken");
        CheckpointWriteQueue queue = queue(storage, Duration.ofDays(1));
        queue.schedule(path("broken"), PLAYER, snapshot(1, 1, "broken"));
        queue.schedule(path("healthy"), PLAYER, snapshot(1, 1, "healthy"));
        ExecutionException error = assertThrows(ExecutionException.class, () -> await(queue.flush()));
        assertTrue(error.getCause().getMessage().contains("broken"));
        assertEquals("healthy", storage.files.get(path("healthy")).getString("Value"));
    }

    @Test
    void shutdownDrainsDelayedWritesAndReleasesTheWorker() throws Exception {
        MemoryStorage storage = storage();
        CheckpointWriteQueue queue = queue(storage, Duration.ofDays(1));
        queue.schedule(path("first-world"), PLAYER, snapshot(1, 1, "first"));
        queue.shutdown(Duration.ofSeconds(5));
        assertFalse(queue.isRunning());
        assertEquals("first", storage.files.get(path("first-world")).getString("Value"));
        queue.schedule(path("second-world"), PLAYER, snapshot(1, 1, "second"));
        queue.shutdown(Duration.ofSeconds(5));
        assertFalse(queue.isRunning());
        assertEquals("second", storage.files.get(path("second-world")).getString("Value"));
    }

    @Test
    void timedOutShutdownStillRecoversAFailedInFlightWriteBeforeRestart() throws Exception {
        MemoryStorage storage = storage();
        storage.blockFirstWrite = true;
        storage.failFirstWrite = true;
        CheckpointWriteQueue queue = queue(storage, Duration.ZERO);
        Path path = path("player");
        queue.schedule(path, PLAYER, snapshot(1, 1, "recover on close"));
        assertTrue(storage.started.await(5, TimeUnit.SECONDS));
        assertThrows(TimeoutException.class, () -> queue.shutdown(Duration.ofMillis(1)));
        assertThrows(RejectedExecutionException.class,
                () -> queue.schedule(path, PLAYER, snapshot(2, 2, "too early")));
        storage.release.countDown();
        await(queue.flush());
        queue.shutdown(Duration.ofSeconds(5));
        assertEquals("recover on close", storage.files.get(path).getString("Value"));
        assertFalse(queue.isRunning());
    }

    @Test
    void boundedVersionCacheReloadsDiskBeforeAcceptingAStaleWrite() throws Exception {
        MemoryStorage storage = storage();
        CheckpointWriteQueue queue = queue(storage, Duration.ofDays(1));
        Path original = path("original");
        await(queue.writeNow(original, PLAYER, snapshot(99, 100, "must survive eviction")));
        for (int i = 0; i < 4096; i++) {
            await(queue.writeNow(path("other-" + i), PLAYER, snapshot(1, 1, "other")));
        }
        await(queue.writeNow(original, PLAYER, snapshot(1, 1, "stale")));
        assertEquals("must survive eviction", storage.files.get(original).getString("Value"));
    }

    @Test
    void enforcesTheCombinedMemoryBudgetAndReleasesItOnDelete() throws Exception {
        MemoryStorage storage = storage();
        CompoundTag tag = snapshot(1, 1, "bounded");
        CheckpointWriteQueue queue = new CheckpointWriteQueue(
                storage, Duration.ofDays(1), 8, tag.sizeInBytes() + 10L);
        queues.add(queue);
        queue.schedule(path("first"), PLAYER, tag);
        assertThrows(RejectedExecutionException.class, () -> queue.schedule(path("second"), PLAYER, tag));
        await(queue.delete(path("first")));
        queue.schedule(path("second"), PLAYER, tag);
        await(queue.flush());
        assertTrue(storage.files.containsKey(path("second")));
    }

    @Test
    void persistentIoFailureHasAFiniteRetryBudget() throws Exception {
        MemoryStorage storage = storage();
        storage.failAllWrites = true;
        CheckpointWriteQueue queue = queue(storage, Duration.ZERO);
        queue.schedule(path("player"), PLAYER, snapshot(1, 1, "cannot write"));
        assertTrue(storage.threeAttempts.await(5, TimeUnit.SECONDS));
        await(queue.read(path("player"), PLAYER));
        assertEquals(3, storage.writes.get());
    }

    private void verifyDeleteDuringWrite(boolean fail) throws Exception {
        MemoryStorage storage = storage();
        storage.blockFirstWrite = true;
        storage.failFirstWrite = fail;
        CheckpointWriteQueue queue = queue(storage, Duration.ZERO);
        Path path = path("player");
        queue.schedule(path, PLAYER, snapshot(1, 1, "in flight"));
        assertTrue(storage.started.await(5, TimeUnit.SECONDS));
        Future<?> deletion = queue.delete(path);
        storage.release.countDown();
        await(deletion);
        await(queue.flush());
        assertFalse(storage.files.containsKey(path));
        assertEquals(1, storage.writes.get());
    }

    private MemoryStorage storage() {
        MemoryStorage storage = new MemoryStorage();
        storages.add(storage);
        return storage;
    }

    private CheckpointWriteQueue queue(MemoryStorage storage, Duration delay) {
        CheckpointWriteQueue queue = new CheckpointWriteQueue(storage, delay, 8, 1024 * 1024);
        queues.add(queue);
        return queue;
    }

    private Path path(String name) {
        return directory.resolve(name + ".dat");
    }

    private static CompoundTag snapshot(long revision, long time, String value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Value", value);
        return ArcQuestPlayerPersistenceMetadata.stamp(tag, revision, time);
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.get(5, TimeUnit.SECONDS);
    }

    private static final class MemoryStorage implements CheckpointWriteQueue.Storage {
        private final Map<Path, CompoundTag> files = new ConcurrentHashMap<>();
        private final AtomicInteger writes = new AtomicInteger();
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final CountDownLatch threeAttempts = new CountDownLatch(3);
        private volatile boolean blockFirstWrite;
        private volatile boolean failFirstWrite;
        private volatile boolean failAllWrites;
        private Path failingPath;

        @Override
        public CompoundTag read(Path path, UUID playerUuid) {
            return files.getOrDefault(path, new CompoundTag()).copy();
        }

        @Override
        public void write(Path path, UUID playerUuid, CompoundTag snapshot) throws IOException {
            int attempt = writes.incrementAndGet();
            threeAttempts.countDown();
            if (blockFirstWrite && attempt == 1) {
                started.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Test writer was not released");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Test writer interrupted", exception);
                }
            }
            if (failAllWrites || (failFirstWrite && attempt == 1) || path.equals(failingPath)) {
                throw new IOException("Simulated storage failure");
            }
            files.put(path, snapshot.copy());
        }

        @Override
        public void delete(Path path) {
            files.remove(path);
        }
    }
}
