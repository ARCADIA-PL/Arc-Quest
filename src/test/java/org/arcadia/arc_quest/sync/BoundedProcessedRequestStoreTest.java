package org.arcadia.arc_quest.sync;

import org.arcadia.arc_quest.core.identity.PlayerSessionRef;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedProcessedRequestStoreTest {

    @Test
    void duplicateRequestReplaysFirstResultWithoutExecutingAgain() {
        BoundedProcessedRequestStore<String> store = new BoundedProcessedRequestStore<>(4);
        PlayerSessionRef session = new PlayerSessionRef(UUID.randomUUID(), 1L);
        UUID requestId = UUID.randomUUID();
        AtomicInteger executions = new AtomicInteger();

        var first = store.process(session, requestId, () -> "result-" + executions.incrementAndGet());
        var replay = store.process(session, requestId, () -> "result-" + executions.incrementAndGet());

        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        assertEquals("result-1", replay.result());
        assertEquals(1, executions.get());
    }

    @Test
    void sameRequestIdIsIndependentAcrossLoginEpochs() {
        BoundedProcessedRequestStore<Integer> store = new BoundedProcessedRequestStore<>(4);
        UUID playerId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        AtomicInteger executions = new AtomicInteger();

        store.process(new PlayerSessionRef(playerId, 1L), requestId, executions::incrementAndGet);
        store.process(new PlayerSessionRef(playerId, 2L), requestId, executions::incrementAndGet);

        assertEquals(2, executions.get());
    }

    @Test
    void boundedWindowEvictsOldestResult() {
        BoundedProcessedRequestStore<Integer> store = new BoundedProcessedRequestStore<>(2);
        PlayerSessionRef session = new PlayerSessionRef(UUID.randomUUID(), 1L);
        UUID first = UUID.randomUUID();
        AtomicInteger executions = new AtomicInteger();

        store.process(session, first, executions::incrementAndGet);
        store.process(session, UUID.randomUUID(), executions::incrementAndGet);
        store.process(session, UUID.randomUUID(), executions::incrementAndGet);
        store.process(session, first, executions::incrementAndGet);

        assertEquals(4, executions.get());
        assertEquals(2, store.trackedRequestCount(session));
    }
}
