package org.arcadia.arc_quest.core.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpiringStateStoreTest {

    @Test
    void putIfAbsentAndTakeAreAtomicPerKey() {
        ExpiringStateStore<String, String> store = new ExpiringStateStore<>();

        assertTrue(store.putIfAbsent("key", "first", 100L));
        assertFalse(store.putIfAbsent("key", "second", 200L));
        assertEquals("first", store.take("key", 100L).value());
        assertEquals(ExpiringStateStore.TakeStatus.MISSING, store.take("key", 100L).status());
    }

    @Test
    void expirationKeepsLegacyStrictGreaterThanBoundary() {
        ExpiringStateStore<String, String> store = new ExpiringStateStore<>();
        store.putIfAbsent("active", "value", 100L);
        store.putIfAbsent("expired", "value", 100L);

        assertEquals(ExpiringStateStore.TakeStatus.ACTIVE, store.take("active", 100L).status());
        assertEquals(ExpiringStateStore.TakeStatus.EXPIRED, store.take("expired", 101L).status());
    }

    @Test
    void cleanupRemovesOnlyExpiredEntries() {
        ExpiringStateStore<String, String> store = new ExpiringStateStore<>();
        store.putIfAbsent("active", "value", 100L);
        store.putIfAbsent("expired", "value", 99L);

        assertEquals(1, store.cleanupExpired(100L));
        assertEquals(1, store.size());
    }
}
