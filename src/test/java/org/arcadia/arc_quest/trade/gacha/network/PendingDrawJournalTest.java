package org.arcadia.arc_quest.trade.gacha.network;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.arcadia.arc_quest.trade.gacha.network.PendingDrawJournal.Stage.*;
import static org.junit.jupiter.api.Assertions.*;

class PendingDrawJournalTest {
    @TempDir Path directory;

    @Test
    void recordsSurviveRestartAtEveryIrreversibleBoundary() throws Exception {
        var entry = entry();
        PendingDrawJournal journal = new PendingDrawJournal(directory, 4);
        try {
            assertTrue(journal.loaded().get(5, TimeUnit.SECONDS).isEmpty());
            journal.prepare(entry).get(5, TimeUnit.SECONDS);
        } finally {
            journal.shutdown(Duration.ofSeconds(5));
        }
        PendingDrawJournal.Stage previous = PREPARED;
        for (var next : List.of(PAID, DELIVERING, DELIVERED)) {
            journal = new PendingDrawJournal(directory, 4);
            try {
                var loaded = journal.loaded().get(5, TimeUnit.SECONDS);
                assertEquals(1, loaded.size());
                assertEquals(previous, loaded.get(0).stage());
                assertEquals(entry.transactionId(), loaded.get(0).transactionId());
                assertEquals(entry.payload(), loaded.get(0).payload());
                journal.advance(entry.transactionId(), previous, next).get(5, TimeUnit.SECONDS);
            } finally {
                journal.shutdown(Duration.ofSeconds(5));
            }
            previous = next;
        }
        journal = new PendingDrawJournal(directory, 4);
        try {
            assertEquals(DELIVERED, journal.loaded().get(5, TimeUnit.SECONDS).get(0).stage());
            journal.remove(entry.transactionId(), DELIVERED).get(5, TimeUnit.SECONDS);
        } finally {
            journal.shutdown(Duration.ofSeconds(5));
        }
        assertFalse(Files.exists(directory.resolve(entry.transactionId() + ".dat")));
    }

    @Test
    void duplicateStaleAndBackwardOperationsCannotOverwriteEvidence() throws Exception {
        var entry = entry();
        var journal = new PendingDrawJournal(directory, 2);
        try {
            journal.loaded().get(5, TimeUnit.SECONDS);
            journal.prepare(entry).get(5, TimeUnit.SECONDS);
            assertThrows(ExecutionException.class, () -> journal.prepare(entry).get(5, TimeUnit.SECONDS));
            journal.advance(entry.transactionId(), PREPARED, PAID).get(5, TimeUnit.SECONDS);
            assertThrows(ExecutionException.class,
                    () -> journal.advance(entry.transactionId(), PREPARED, PAID).get(5, TimeUnit.SECONDS));
            assertThrows(IllegalArgumentException.class, () -> journal.advance(entry.transactionId(), PAID, PREPARED));
            assertThrows(IllegalArgumentException.class, () -> journal.remove(entry.transactionId(), PAID));
            journal.advance(entry.transactionId(), PAID, REVIEW).get(5, TimeUnit.SECONDS);
            assertThrows(IllegalArgumentException.class, () -> journal.remove(entry.transactionId(), REVIEW));
        } finally {
            journal.shutdown(Duration.ofSeconds(5));
        }
        var restarted = new PendingDrawJournal(directory, 2);
        try {
            assertEquals(REVIEW, restarted.loaded().get(5, TimeUnit.SECONDS).get(0).stage());
        } finally {
            restarted.shutdown(Duration.ofSeconds(5));
        }
    }

    @Test
    void corruptFileStopsAdmissionAndIsNeverDiscarded() throws Exception {
        Path corrupt = directory.resolve(UUID.randomUUID() + ".dat");
        Files.writeString(corrupt, "broken");
        var journal = new PendingDrawJournal(directory, 2);
        try {
            assertThrows(ExecutionException.class, () -> journal.loaded().get(5, TimeUnit.SECONDS));
            assertThrows(ExecutionException.class, () -> journal.prepare(entry()).get(5, TimeUnit.SECONDS));
            assertEquals("broken", Files.readString(corrupt));
        } finally {
            journal.shutdown(Duration.ofSeconds(5));
        }
    }

    @Test
    void fullJournalRejectsNewDrawsWithoutEvictingPaidRewards() throws Exception {
        var entry = entry();
        var journal = new PendingDrawJournal(directory, 1);
        try {
            journal.loaded().get(5, TimeUnit.SECONDS);
            journal.prepare(entry).get(5, TimeUnit.SECONDS);
            journal.advance(entry.transactionId(), PREPARED, PAID).get(5, TimeUnit.SECONDS);
            assertThrows(ExecutionException.class, () -> journal.prepare(entry()).get(5, TimeUnit.SECONDS));
        } finally {
            journal.shutdown(Duration.ofSeconds(5));
        }
        var restarted = new PendingDrawJournal(directory, 1);
        try {
            assertEquals(PAID, restarted.loaded().get(5, TimeUnit.SECONDS).get(0).stage());
        } finally {
            restarted.shutdown(Duration.ofSeconds(5));
        }
    }

    @Test
    void callerMutationAndCancelledObserverCannotChangeDurablePayload() throws Exception {
        CompoundTag source = new CompoundTag();
        source.putInt("Count", 2);
        var entry = new PendingDrawJournal.Entry(UUID.randomUUID(), UUID.randomUUID(), "shop", PREPARED, source);
        source.putInt("Count", 200);
        entry.payload().putInt("Count", 300);
        var journal = new PendingDrawJournal(directory, 2);
        try {
            journal.loaded().cancel(false);
            journal.prepare(entry).get(5, TimeUnit.SECONDS);
        } finally {
            journal.shutdown(Duration.ofSeconds(5));
        }
        var restarted = new PendingDrawJournal(directory, 2);
        try {
            assertEquals(2, restarted.loaded().get(5, TimeUnit.SECONDS).get(0).payload().getInt("Count"));
        } finally {
            restarted.shutdown(Duration.ofSeconds(5));
        }
    }

    @Test
    void diskWriteAcknowledgesOnlyAfterCompletionAndNeverRunsOnCallerThread() throws Exception {
        Thread caller = Thread.currentThread();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var storage = new MemoryStorage() {
            @Override
            public void write(Path path, PendingDrawJournal.Entry entry) throws IOException {
                assertNotSame(caller, Thread.currentThread());
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("test release timed out");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException(exception);
                }
                super.write(path, entry);
            }
        };
        var journal = new PendingDrawJournal(directory, 2, storage);
        try {
            journal.loaded().get(5, TimeUnit.SECONDS);
            var pending = journal.prepare(entry());
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertFalse(pending.isDone());
            release.countDown();
            pending.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            journal.shutdown(Duration.ofSeconds(5));
        }
        assertTrue(journal.isTerminated());
        assertThrows(ExecutionException.class, () -> journal.prepare(entry()).get(5, TimeUnit.SECONDS));
    }

    @Test
    void ambiguousWriteFailureDisablesFurtherWrites() throws Exception {
        var storage = new MemoryStorage() {
            @Override
            public void write(Path path, PendingDrawJournal.Entry entry) throws IOException {
                super.write(path, entry);
                throw new IOException("disk write may have succeeded");
            }
        };
        var journal = new PendingDrawJournal(directory, 2, storage);
        try {
            journal.loaded().get(5, TimeUnit.SECONDS);
            assertThrows(ExecutionException.class, () -> journal.prepare(entry()).get(5, TimeUnit.SECONDS));
            assertThrows(ExecutionException.class, () -> journal.prepare(entry()).get(5, TimeUnit.SECONDS));
            assertEquals(1, storage.entries.size());
        } finally {
            journal.shutdown(Duration.ofSeconds(5));
        }
    }

    @Test
    void mismatchedEnvelopeAndOverCapacityStartupDoNotPermitNewPayments() throws Exception {
        var entry = entry();
        var journal = new PendingDrawJournal(directory, 2);
        try {
            journal.loaded().get(5, TimeUnit.SECONDS);
            journal.prepare(entry).get(5, TimeUnit.SECONDS);
            journal.prepare(entry()).get(5, TimeUnit.SECONDS);
        } finally {
            journal.shutdown(Duration.ofSeconds(5));
        }
        var tooSmall = new PendingDrawJournal(directory, 1);
        try {
            assertThrows(ExecutionException.class, () -> tooSmall.loaded().get(5, TimeUnit.SECONDS));
            assertThrows(ExecutionException.class, () -> tooSmall.prepare(entry()).get(5, TimeUnit.SECONDS));
        } finally {
            tooSmall.shutdown(Duration.ofSeconds(5));
        }
        Path original = directory.resolve(entry.transactionId() + ".dat");
        Path renamed = directory.resolve(UUID.randomUUID() + ".dat");
        Files.move(original, renamed);
        var mismatched = new PendingDrawJournal(directory, 2);
        try {
            assertThrows(ExecutionException.class, () -> mismatched.loaded().get(5, TimeUnit.SECONDS));
            assertTrue(Files.exists(renamed));
        } finally {
            mismatched.shutdown(Duration.ofSeconds(5));
        }
    }

    @Test
    void onlyUnpaidCancellationReleasesCapacityBeforeDelivery() throws Exception {
        var entry = entry();
        var journal = new PendingDrawJournal(directory, 1);
        try {
            journal.loaded().get(5, TimeUnit.SECONDS);
            journal.prepare(entry).get(5, TimeUnit.SECONDS);
            journal.remove(entry.transactionId(), PREPARED).get(5, TimeUnit.SECONDS);
            var replacement = entry();
            journal.prepare(replacement).get(5, TimeUnit.SECONDS);
            assertThrows(ExecutionException.class,
                    () -> journal.remove(entry.transactionId(), PREPARED).get(5, TimeUnit.SECONDS));
            journal.advance(replacement.transactionId(), PREPARED, PAID).get(5, TimeUnit.SECONDS);
            assertThrows(ExecutionException.class,
                    () -> journal.remove(replacement.transactionId(), PREPARED).get(5, TimeUnit.SECONDS));
        } finally {
            journal.shutdown(Duration.ofSeconds(5));
        }
    }

    private static PendingDrawJournal.Entry entry() {
        CompoundTag payload = new CompoundTag();
        payload.putString("Item", "minecraft:diamond");
        payload.putInt("Count", 3);
        return new PendingDrawJournal.Entry(UUID.randomUUID(), UUID.randomUUID(), "arc_quest:shop", PREPARED, payload);
    }

    @Test
    void shutdownTimeoutKeepsAcceptedWriteAliveAndRejectsNewWrites() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var storage = new MemoryStorage() {
            @Override public void write(Path path, PendingDrawJournal.Entry entry) throws IOException {
                started.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Test timed out");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException(interrupted);
                }
                super.write(path, entry);
            }
        };
        var journal = new PendingDrawJournal(directory, 2, storage);
        try {
            journal.loaded().get(5, TimeUnit.SECONDS);
            var accepted = journal.prepare(entry());
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.TimeoutException.class, () -> journal.shutdown(Duration.ofMillis(1)));
            assertFalse(journal.isTerminated());
            assertThrows(ExecutionException.class, () -> journal.prepare(entry()).get(5, TimeUnit.SECONDS));
            release.countDown();
            accepted.get(5, TimeUnit.SECONDS);
            assertEquals(1, storage.entries.size());
        } finally { release.countDown(); journal.shutdown(Duration.ofSeconds(5)); }
        assertTrue(journal.isTerminated());
    }

    private static class MemoryStorage implements PendingDrawJournal.Storage {
        final List<PendingDrawJournal.Entry> entries = new ArrayList<>();
        @Override public List<Path> entries(Path directory, int limit) { return List.of(); }
        @Override public PendingDrawJournal.Entry read(Path path) { throw new UnsupportedOperationException(); }
        @Override public void write(Path path, PendingDrawJournal.Entry entry) throws IOException { entries.add(entry); }
        @Override public void delete(Path path) { throw new UnsupportedOperationException(); }
        @Override public void archive(Path path) { throw new UnsupportedOperationException(); }
    }
}
