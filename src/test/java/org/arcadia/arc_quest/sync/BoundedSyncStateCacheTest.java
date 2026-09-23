package org.arcadia.arc_quest.sync;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoundedSyncStateCacheTest {
    @Test
    void failedSendDoesNotSuppressRetryOrForgetPreviousSuccessfulState() {
        var cache = new BoundedSyncStateCache<String, String>(2);
        assertTrue(cache.send("shop", "old", false, () -> {}));
        assertThrows(IllegalStateException.class, () -> cache.send("shop", "new", false, () -> {
            throw new IllegalStateException("Network unavailable");
        }));
        assertFalse(cache.send("shop", "old", false, () -> fail()));
        assertTrue(cache.send("shop", "new", false, () -> {}));
        assertFalse(cache.send("shop", "new", false, () -> fail()));
    }

    @Test
    void equalHashCodesDoNotHideDifferentStates() {
        var cache = new BoundedSyncStateCache<String, String>(2);
        assertEquals("Aa".hashCode(), "BB".hashCode());
        List<String> sent = new ArrayList<>();
        assertTrue(cache.send("shop", "Aa", false, () -> sent.add("Aa")));
        assertTrue(cache.send("shop", "BB", false, () -> sent.add("BB")));
        assertEquals(List.of("Aa", "BB"), sent);
    }

    @Test
    void explicitOpenAlwaysSendsEvenIfStateIsUnchanged() {
        var cache = new BoundedSyncStateCache<String, String>(2);
        cache.send("shop", "state", false, () -> {});
        assertTrue(cache.send("shop", "state", true, () -> {}));
    }

    @Test
    void capacityEvictsLeastRecentlyUsedStateAndNeverBlocksResending() {
        var cache = new BoundedSyncStateCache<String, String>(2);
        cache.send("a", "state", false, () -> {});
        cache.send("b", "state", false, () -> {});
        assertFalse(cache.send("a", "state", false, () -> fail()));
        cache.send("c", "state", false, () -> {});
        assertEquals(2, cache.size());
        assertFalse(cache.send("a", "state", false, () -> fail()));
        assertTrue(cache.send("b", "state", false, () -> {}));
    }

    @Test
    void playerCleanupInvalidatesOnlyMatchingEntriesAndShutdownClearsAll() {
        var cache = new BoundedSyncStateCache<String, String>(3);
        cache.send("p1/shop", "state", false, () -> {});
        cache.send("p2/shop", "state", false, () -> {});
        cache.removeIf(key -> key.startsWith("p1/"));
        assertTrue(cache.send("p1/shop", "state", false, () -> {}));
        assertFalse(cache.send("p2/shop", "state", false, () -> fail()));
        cache.clear();
        assertEquals(0, cache.size());
        assertTrue(cache.send("p2/shop", "state", false, () -> {}));
    }
}
