package org.arcadia.arc_quest.sync;

import org.arcadia.arc_quest.core.identity.PlayerSessionRef;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.arcadia.arc_quest.sync.BoundedProcessedRequestStore.Rejection.*;
import static org.junit.jupiter.api.Assertions.*;

class BoundedProcessedRequestStoreTest {
    private final BoundedProcessedRequestStore<String> store = new BoundedProcessedRequestStore<>(2, 2);
    private final PlayerSessionRef player = new PlayerSessionRef(UUID.randomUUID(), 1);

    @Test
    void replayReturnsOriginalResultWithoutExecutingAgain() {
        UUID id = UUID.randomUUID();
        var first = store.process(player, id, "shop/item", () -> "paid");
        var replay = store.process(player, id, "shop/item", () -> fail("Duplicate payment"));
        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        assertEquals(first.result(), replay.result());
    }

    @Test
    void sameRequestIdCannotBeReboundToAnotherPurchaseEvenWithHashCollision() {
        UUID id = UUID.randomUUID();
        assertEquals("Aa".hashCode(), "BB".hashCode());
        store.process(player, id, "Aa", () -> "paid");
        assertRejected(CONTENT_MISMATCH, () -> store.process(player, id, "BB", () -> fail("Wrong purchase")));
    }

    @Test
    void callbackCannotReenterWithSameDifferentOrLegacyRequest() {
        UUID id = UUID.randomUUID();
        store.process(player, id, () -> {
            assertRejected(IN_PROGRESS, () -> store.process(player, id, () -> fail()));
            assertRejected(IN_PROGRESS, () -> store.process(player, UUID.randomUUID(), () -> fail()));
            assertRejected(IN_PROGRESS, () -> store.process(player, null, () -> fail()));
            assertRejected(IN_PROGRESS, () -> store.process(new PlayerSessionRef(player.playerUuid(), 2),
                    UUID.randomUUID(), () -> fail()));
            return "done";
        });
        assertEquals("next", store.process(player, UUID.randomUUID(), () -> "next").result());
    }

    @Test
    void partiallyFailedOperationCannotBeRetriedOrEvicted() {
        UUID failed = UUID.randomUUID();
        AtomicInteger effects = new AtomicInteger();
        RuntimeException original = new IllegalStateException("Payment took effect before exception");
        assertSame(original, assertThrows(IllegalStateException.class, () -> store.process(player, failed, () -> {
            effects.incrementAndGet();
            throw original;
        })));
        for (int i = 0; i < 5; i++) store.process(player, UUID.randomUUID(), () -> "success");
        assertRejected(PREVIOUS_FAILURE, () -> store.process(player, failed, () -> { effects.incrementAndGet(); return "bad"; }));
        assertEquals(1, effects.get());
        assertEquals(2, store.trackedRequestCount(player));
    }

    @Test
    void nullResultIsAlsoAnUnresolvedFailure() {
        UUID id = UUID.randomUUID();
        assertThrows(NullPointerException.class, () -> store.process(player, id, () -> null));
        assertRejected(PREVIOUS_FAILURE, () -> store.process(player, id, () -> fail()));
    }

    @Test
    void capacityFilledWithFailuresRejectsBeforeRunningMoreOperations() {
        for (int i = 0; i < 2; i++) {
            assertThrows(IllegalStateException.class, () -> store.process(player, UUID.randomUUID(), () -> {
                throw new IllegalStateException("Unresolved");
            }));
        }
        assertRejected(CAPACITY, () -> store.process(player, UUID.randomUUID(), () -> fail("No room to retain result")));
    }

    @Test
    void legacyRequestsRemainRepeatableButAreStillProtectedDuringExecution() {
        AtomicInteger calls = new AtomicInteger();
        for (UUID id : new UUID[]{null, RequestIdempotencyStore.LEGACY_REQUEST_ID, null}) {
            var result = store.process(player, id, () -> {
                calls.incrementAndGet();
                assertRejected(IN_PROGRESS, () -> store.process(player, null, () -> fail()));
                return "legacy";
            });
            assertFalse(result.replayed());
        }
        assertEquals(3, calls.get());
        assertEquals(0, store.trackedRequestCount(player));
    }

    @Test
    void sessionCapacityRequiresExplicitLifecycleCleanup() {
        var second = new PlayerSessionRef(UUID.randomUUID(), 1);
        var third = new PlayerSessionRef(UUID.randomUUID(), 1);
        store.process(player, UUID.randomUUID(), () -> "one");
        store.process(second, UUID.randomUUID(), () -> "two");
        assertRejected(CAPACITY, () -> store.process(third, UUID.randomUUID(), () -> fail()));
        store.clearPlayer(player.playerUuid());
        assertEquals("three", store.process(third, UUID.randomUUID(), () -> "three").result());
        assertEquals(1, store.trackedRequestCount(second));
    }

    @Test
    void cleanupDuringCallbackDoesNotReleaseInFlightGateOrResurrectCache() {
        store.process(player, UUID.randomUUID(), () -> {
            store.clearPlayer(player.playerUuid());
            store.clear();
            assertRejected(IN_PROGRESS, () -> store.process(player, UUID.randomUUID(), () -> fail()));
            return "done";
        });
        assertEquals(0, store.trackedRequestCount(player));
        assertEquals("new session", store.process(player, UUID.randomUUID(), () -> "new session").result());
    }

    @Test
    void otherPlayersCanProgressWhileOneOperationRunsWithoutHoldingStoreLock() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        UUID id = UUID.randomUUID();
        try {
            var first = executor.submit(() -> store.process(player, id, () -> {
                entered.countDown();
                try {
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(failure);
                }
                return "first";
            }));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            var second = executor.submit(() -> {
                assertRejected(IN_PROGRESS, () -> store.process(player, id, () -> fail("Concurrent duplicate")));
                return store.process(new PlayerSessionRef(UUID.randomUUID(), 1), UUID.randomUUID(), () -> "second");
            });
            assertEquals("second", second.get(2, TimeUnit.SECONDS).result());
            release.countDown();
            assertEquals("first", first.get(2, TimeUnit.SECONDS).result());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    private static void assertRejected(BoundedProcessedRequestStore.Rejection reason, Runnable operation) {
        assertEquals(reason, assertThrows(BoundedProcessedRequestStore.RequestRejectedException.class, operation::run).reason());
    }
}
