package org.arcadia.arc_quest.trade.gacha.network;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.arcadia.arc_quest.trade.gacha.network.PendingDrawCommit.Decision.CANCEL;
import static org.arcadia.arc_quest.trade.gacha.network.PendingDrawCommit.Decision.WAIT;
import static org.arcadia.arc_quest.trade.gacha.network.PendingDrawJournal.Stage.*;
import static org.junit.jupiter.api.Assertions.*;

class PendingDrawCommitTest {
    @TempDir Path directory;

    @Test
    void paymentAndDeliveryWaitForAllDurableAcknowledgements() throws Exception {
        var storage = new ControlledStorage();
        var journal = new PendingDrawJournal(directory, 4, storage);
        AtomicInteger payments = new AtomicInteger();
        Supplier<PendingDrawCommit.Decision> pay = () -> { payments.incrementAndGet(); return PendingDrawCommit.Decision.PAID; };
        try {
            journal.loaded().get(5, TimeUnit.SECONDS);
            var gate = new PendingDrawCommit(journal, entry());
            var prepared = storage.next();
            assertEquals(PREPARED, prepared.stage);
            assertFalse(gate.poll(pay));
            assertEquals(0, payments.get());
            prepared.release.countDown();
            await(() -> {
                gate.poll(() -> {
                    assertFalse(gate.poll(() -> fail("Reentrant extension must not charge again")));
                    return pay.get();
                });
                return payments.get() == 1;
            });
            var paid = storage.next();
            assertEquals(PendingDrawJournal.Stage.PAID, paid.stage);
            assertFalse(gate.poll(pay));
            paid.release.countDown();
            await(() -> { gate.poll(pay); return gate.entry().stage() == DELIVERING; });
            var authorized = storage.next();
            assertEquals(DELIVERING, authorized.stage);
            assertFalse(gate.poll(pay));
            authorized.release.countDown();
            await(() -> gate.poll(pay));
            assertTrue(gate.poll(() -> fail("Cannot charge twice")));
            assertFalse(gate.canceled());
            assertEquals(1, payments.get());
        } finally { storage.releaseAll(); journal.shutdown(Duration.ofSeconds(5)); }
    }

    @Test
    void preparedWriteFailureNeverInvokesPayment() throws Exception {
        var storage = new ControlledStorage();
        var journal = new PendingDrawJournal(directory, 4, storage);
        try {
            journal.loaded().get(5, TimeUnit.SECONDS);
            var gate = new PendingDrawCommit(journal, entry());
            var prepared = storage.next();
            prepared.fail = true;
            prepared.release.countDown();
            await(() -> {
                try { gate.poll(() -> fail("Payment before durable intent")); return false; }
                catch (CompletionException expected) { return true; }
            });
            assertThrows(CompletionException.class, () -> gate.poll(() -> fail("Retry payment after I/O failure")));
        } finally { storage.releaseAll(); journal.shutdown(Duration.ofSeconds(5)); }
    }

    @Test
    void paymentExceptionCannotBeRetriedEvenIfCallbackIsPolledAgain() throws Exception {
        var storage = new ControlledStorage();
        var journal = new PendingDrawJournal(directory, 4, storage);
        var failure = new IllegalStateException("partly charged custom currency");
        AtomicInteger attempts = new AtomicInteger();
        try {
            journal.loaded().get(5, TimeUnit.SECONDS);
            var gate = new PendingDrawCommit(journal, entry());
            storage.next().release.countDown();
            await(() -> {
                try { gate.poll(() -> { attempts.incrementAndGet(); throw failure; }); return false; }
                catch (IllegalStateException thrown) { assertSame(failure, thrown); return true; }
            });
            assertSame(failure, assertThrows(IllegalStateException.class, () -> gate.poll(() -> fail("No replay"))));
            assertEquals(1, attempts.get());
            assertEquals(PREPARED, gate.entry().stage());
        } finally { storage.releaseAll(); journal.shutdown(Duration.ofSeconds(5)); }
    }

    @Test
    void canceledSessionWaitsForDeletionAndNeverAuthorizesDelivery() throws Exception {
        var storage = new ControlledStorage();
        var journal = new PendingDrawJournal(directory, 4, storage);
        try {
            journal.loaded().get(5, TimeUnit.SECONDS);
            var gate = new PendingDrawCommit(journal, entry());
            storage.next().release.countDown();
            AtomicInteger checks = new AtomicInteger();
            await(() -> { gate.poll(() -> { checks.incrementAndGet(); return WAIT; }); return checks.get() > 0; });
            assertEquals(PREPARED, gate.entry().stage());
            assertFalse(gate.poll(() -> CANCEL));
            var deletion = storage.next();
            assertNull(deletion.stage);
            assertFalse(gate.poll(() -> fail("Canceled session must never charge")));
            deletion.release.countDown();
            await(() -> gate.poll(() -> fail("Canceled session must never charge")));
            assertTrue(gate.canceled());
        } finally { storage.releaseAll(); journal.shutdown(Duration.ofSeconds(5)); }
    }

    @Test
    void paidWriteFailureNeverReleasesResultOrChargesAgain() throws Exception {
        var storage = new ControlledStorage();
        var journal = new PendingDrawJournal(directory, 4, storage);
        AtomicInteger payments = new AtomicInteger();
        try {
            journal.loaded().get(5, TimeUnit.SECONDS);
            var gate = new PendingDrawCommit(journal, entry());
            storage.next().release.countDown();
            await(() -> { gate.poll(() -> { payments.incrementAndGet(); return PendingDrawCommit.Decision.PAID; }); return payments.get() == 1; });
            var paid = storage.next();
            paid.fail = true;
            paid.release.countDown();
            await(() -> {
                try { assertFalse(gate.poll(() -> fail("Duplicate charge"))); return false; }
                catch (CompletionException expected) { return true; }
            });
            assertEquals(1, payments.get());
        } finally { storage.releaseAll(); journal.shutdown(Duration.ofSeconds(5)); }
    }

    @Test
    void shutdownCancelsQueuedUnpaidIntentWithoutInvokingPayment() throws Exception {
        var storage = new ControlledStorage();
        var journal = new PendingDrawJournal(directory, 4, storage);
        try {
            journal.loaded().get(5, TimeUnit.SECONDS);
            var gate = new PendingDrawCommit(journal, entry());
            var prepared = storage.next();
            gate.cancelUnpaid();
            assertFalse(gate.poll(() -> fail("Stopping server cannot charge")));
            prepared.release.countDown();
            var deletion = storage.next();
            assertNull(deletion.stage);
            deletion.release.countDown();
            await(() -> gate.poll(() -> fail("Stopping server cannot charge")));
            assertTrue(gate.canceled());
        } finally { storage.releaseAll(); journal.shutdown(Duration.ofSeconds(5)); }
    }

    private static PendingDrawJournal.Entry entry() {
        return new PendingDrawJournal.Entry(UUID.randomUUID(), UUID.randomUUID(), "arc_quest:test", PREPARED, new CompoundTag());
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= until) fail("Timed out waiting for journal acknowledgement");
            Thread.sleep(1);
        }
    }

    private static final class Write {
        final PendingDrawJournal.Stage stage;
        final CountDownLatch release = new CountDownLatch(1);
        volatile boolean fail;
        Write(PendingDrawJournal.Stage stage) { this.stage = stage; }
    }

    private static final class ControlledStorage implements PendingDrawJournal.Storage {
        final ArrayBlockingQueue<Write> writes = new ArrayBlockingQueue<>(8);
        final java.util.concurrent.CopyOnWriteArrayList<Write> issued = new java.util.concurrent.CopyOnWriteArrayList<>();
        @Override public List<Path> entries(Path directory, int limit) { return List.of(); }
        @Override public PendingDrawJournal.Entry read(Path path) { throw new UnsupportedOperationException(); }
        @Override public void archive(Path path) { throw new UnsupportedOperationException(); }
        @Override public void write(Path path, PendingDrawJournal.Entry entry) throws IOException { block(entry.stage()); }
        @Override public void delete(Path path) throws IOException { block(null); }

        private void block(PendingDrawJournal.Stage stage) throws IOException {
            var write = new Write(stage);
            issued.add(write);
            writes.add(write);
            try {
                if (!write.release.await(5, TimeUnit.SECONDS)) throw new IOException("Test storage timed out");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException(interrupted);
            }
            if (write.fail) throw new IOException("Injected storage failure");
        }

        Write next() throws Exception {
            Write write = writes.poll(5, TimeUnit.SECONDS);
            assertNotNull(write, "Expected storage operation");
            return write;
        }

        void releaseAll() { issued.forEach(write -> write.release.countDown()); }
    }
}
