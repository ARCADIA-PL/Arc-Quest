package org.arcadia.arc_quest.questplayer.interaction;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PlayerInteractionGuardTest {
    private final PlayerInteractionGuard guard = new PlayerInteractionGuard();
    private final UUID player = UUID.randomUUID();

    @Test
    void restoreCannotInterruptNestedDialogueTradeOrRewardCallbacks() {
        try (var dialogue = guard.enter(player)) {
            assertNotNull(dialogue);
            assertNull(guard.restore(player));
            try (var trade = guard.enter(player)) {
                assertNotNull(trade);
                assertNull(guard.restore(player));
            }
            assertNull(guard.restore(player));
        }
        try (var restore = guard.restore(player)) {
            assertNotNull(restore);
        }
    }

    @Test
    void exclusiveRestoreRejectsNewInteractionsAndRecursiveRestores() {
        try (var restore = guard.restore(player)) {
            assertNotNull(restore);
            assertTrue(guard.isRestoring(player));
            assertNull(guard.enter(player));
            assertNull(guard.restore(player));
        }
        assertFalse(guard.isRestoring(player));
        try (var next = guard.enter(player)) { assertNotNull(next); }
    }

    @Test
    void exceptionReleasesExclusiveScopeWithoutUnlockingOtherScopes() {
        assertThrows(IllegalStateException.class, () -> {
            try (var restore = guard.restore(player)) {
                assertNotNull(restore);
                throw new IllegalStateException("Storage failed");
            }
        });
        try (var retry = guard.restore(player)) { assertNotNull(retry); }
    }

    @Test
    void outOfOrderAndRepeatedCloseCannotReleaseRemainingOrNewScopes() {
        var first = guard.enter(player);
        var second = guard.enter(player);
        first.close();
        first.close();
        assertNull(guard.restore(player));
        second.close();
        try (var restore = guard.restore(player)) {
            first.close();
            second.close();
            assertNull(guard.enter(player));
            assertTrue(guard.isRestoring(player));
        }
    }

    @Test
    void onePlayerRestoreDoesNotPreventOtherPlayersFromInteracting() {
        UUID other = UUID.randomUUID();
        try (var restore = guard.restore(player); var interaction = guard.enter(other)) {
            assertNotNull(restore);
            assertNotNull(interaction);
            assertFalse(guard.isRestoring(other));
            assertNull(guard.restore(other));
        }
    }

    @Test
    void backgroundEntryCannotRaceAnExclusiveRestore() throws Exception {
        var worker = Executors.newSingleThreadExecutor();
        try (var restore = guard.restore(player)) {
            assertNotNull(restore);
            assertNull(worker.submit(() -> guard.enter(player)).get(2, TimeUnit.SECONDS));
            assertNull(worker.submit(() -> guard.restore(player)).get(2, TimeUnit.SECONDS));
        } finally {
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(2, TimeUnit.SECONDS));
        }
    }
}
