package org.arcadia.arc_quest.trade.gacha.network;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PendingDrawQueueTest {
    private final PendingDrawQueue<String> queue = new PendingDrawQueue<>(2);
    private final UUID player = UUID.randomUUID();

    @Test
    void failedPersistenceAfterPublicationDoesNotPermitDelivery() {
        UUID token = queue.reserve(player);
        assertEquals(token, queue.token(player));
        assertTrue(queue.publish(player, token, "shop", "paid reward", 0));
        queue.failReservation(player, token);
        queue.releaseReservation(player, token);
        assertThrows(IllegalStateException.class, () -> queue.deliver(player, "shop", value -> fail("Not durable")));
        assertTrue(queue.duePlayers(Long.MAX_VALUE).isEmpty());
        assertNull(queue.reserve(player));
    }

    @Test
    void wrongShopAndEmptyShopPreserveRewardUntilCorrectConfirmation() {
        ready(player, "reward", 30);
        List<String> granted = new ArrayList<>();
        assertFalse(queue.deliver(player, "wrong", granted::add));
        assertFalse(queue.deliver(player, "", granted::add));
        assertTrue(queue.contains(player));
        assertTrue(queue.deliver(player, "shop", granted::add));
        assertFalse(queue.deliver(player, "shop", granted::add));
        assertEquals(List.of("reward"), granted);
    }

    @Test
    void expiredRewardRemainsAvailableForCompensation() {
        ready(player, "reward", 30);
        assertTrue(queue.duePlayers(29).isEmpty());
        assertEquals(List.of(player), queue.duePlayers(30));
        assertEquals(List.of(player), queue.duePlayers(Long.MAX_VALUE));
        assertTrue(queue.deliver(player, null, value -> assertEquals("reward", value)));
        assertTrue(queue.duePlayers(Long.MAX_VALUE).isEmpty());
    }

    @Test
    void reservationAndPublicationPreventCallbackReentry() {
        UUID token = queue.reserve(player);
        assertNull(queue.reserve(player));
        queue.discard(player);
        assertTrue(queue.contains(player));
        assertTrue(queue.publish(player, token, "shop", "reward", 0));
        assertFalse(queue.publish(player, token, "shop", "replacement", 0));
        assertFalse(queue.deliver(player, "shop", value -> fail("Still preparing")));
        assertTrue(queue.duePlayers(Long.MAX_VALUE).isEmpty());
        queue.discard(player);
        queue.releaseReservation(player, token);
        assertTrue(queue.deliver(player, "shop", value -> {
            assertEquals("reward", value);
            assertNull(queue.reserve(player));
            assertFalse(queue.deliver(player, "shop", nested -> fail("Recursive grant")));
            queue.discard(player);
            assertTrue(queue.contains(player));
        }));
        assertFalse(queue.contains(player));
    }

    @Test
    void partiallyFailedGrantIsQuarantinedAndNeverAutomaticallyRetried() {
        ready(player, "reward", 0);
        RuntimeException failure = new IllegalStateException("Partial grant");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> queue.deliver(player, "shop", value -> { throw failure; })));
        assertTrue(queue.contains(player));
        assertTrue(queue.duePlayers(Long.MAX_VALUE).isEmpty());
        assertNull(queue.reserve(player));
        assertThrows(IllegalStateException.class,
                () -> queue.deliver(player, null, value -> fail("Unsafe retry")));
        queue.discard(player);
        assertNotNull(queue.reserve(player));
    }

    @Test
    void failedIrreversiblePaymentKeepsReservationUntilExplicitResolution() {
        UUID token = queue.reserve(player);
        queue.failReservation(player, token);
        queue.releaseReservation(player, token);
        assertNull(queue.reserve(player));
        assertTrue(queue.duePlayers(Long.MAX_VALUE).isEmpty());
        assertThrows(IllegalStateException.class, () -> queue.deliver(player, null, value -> fail()));
    }

    @Test
    void staleReservationCannotPublishReleaseOrFailANewerDraw() {
        UUID old = queue.reserve(player);
        queue.releaseReservation(player, old);
        UUID current = queue.reserve(player);
        queue.releaseReservation(player, old);
        queue.failReservation(player, old);
        assertFalse(queue.publish(player, old, "shop", "old", 0));
        assertTrue(queue.publish(player, current, "shop", "current", 0));
        queue.releaseReservation(player, current);
        assertTrue(queue.deliver(player, "shop", value -> assertEquals("current", value)));
    }

    @Test
    void capacityRejectsNewRequestsWithoutEvictingPaidRewards() {
        ready(player, "first", 0);
        UUID second = UUID.randomUUID();
        UUID reservation = queue.reserve(second);
        UUID third = UUID.randomUUID();
        assertNull(queue.reserve(third));
        assertTrue(queue.contains(player));
        queue.releaseReservation(second, reservation);
        assertNotNull(queue.reserve(third));
        assertEquals(2, queue.size());
        queue.clear();
        assertEquals(0, queue.size());
    }

    private void ready(UUID owner, String value, long dueAt) {
        UUID token = queue.reserve(owner);
        assertNotNull(token);
        assertTrue(queue.publish(owner, token, "shop", value, dueAt));
        queue.releaseReservation(owner, token);
    }
}
