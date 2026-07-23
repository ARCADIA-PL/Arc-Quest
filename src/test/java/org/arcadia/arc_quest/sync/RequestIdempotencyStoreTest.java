package org.arcadia.arc_quest.sync;

import org.arcadia.arc_quest.core.identity.PlayerSessionRef;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestIdempotencyStoreTest {

    @Test
    void duplicateRequestIsRejectedWithinSameLoginSession() {
        RequestIdempotencyStore store = new RequestIdempotencyStore(4);
        PlayerSessionRef session = new PlayerSessionRef(UUID.randomUUID(), 1L);
        UUID requestId = UUID.randomUUID();

        assertTrue(store.claim(session, requestId));
        assertFalse(store.claim(session, requestId));
    }

    @Test
    void sameRequestIdIsIndependentAcrossLoginEpochs() {
        RequestIdempotencyStore store = new RequestIdempotencyStore(4);
        UUID playerId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        assertTrue(store.claim(new PlayerSessionRef(playerId, 1L), requestId));
        assertTrue(store.claim(new PlayerSessionRef(playerId, 2L), requestId));
    }

    @Test
    void boundedWindowEvictsOldestRequest() {
        RequestIdempotencyStore store = new RequestIdempotencyStore(2);
        PlayerSessionRef session = new PlayerSessionRef(UUID.randomUUID(), 1L);
        UUID first = UUID.randomUUID();

        assertTrue(store.claim(session, first));
        assertTrue(store.claim(session, UUID.randomUUID()));
        assertTrue(store.claim(session, UUID.randomUUID()));
        assertEquals(2, store.trackedRequestCount(session));
        assertTrue(store.claim(session, first));
    }

    @Test
    void clearingPlayerRemovesAllEpochWindows() {
        RequestIdempotencyStore store = new RequestIdempotencyStore(4);
        UUID playerId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        store.claim(new PlayerSessionRef(playerId, 1L), requestId);
        store.claim(new PlayerSessionRef(playerId, 2L), requestId);
        store.clearPlayer(playerId);

        assertTrue(store.claim(new PlayerSessionRef(playerId, 1L), requestId));
    }
}
