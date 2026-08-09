package org.arcadia.arc_quest.questplayer;

import org.arcadia.arc_quest.core.identity.PlayerSessionRef;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlayerSessionStateStoreTest {

    @Test
    void samePlayerInNewSessionDoesNotReusePreviousWorldState() {
        PlayerSessionStateStore<String> store = new PlayerSessionStateStore<>();
        UUID playerId = UUID.randomUUID();
        PlayerSessionRef oldWorldSession = new PlayerSessionRef(playerId, 1L);
        PlayerSessionRef newWorldSession = new PlayerSessionRef(playerId, 2L);

        store.put(oldWorldSession, "old-world-progress");

        assertNull(store.get(newWorldSession));
        assertEquals("new-world-progress",
                store.getOrCreate(newWorldSession, () -> "new-world-progress"));
        assertEquals("old-world-progress", store.get(oldWorldSession));
    }

    @Test
    void removePlayerClearsAllStaleSessions() {
        PlayerSessionStateStore<String> store = new PlayerSessionStateStore<>();
        UUID playerId = UUID.randomUUID();
        store.put(new PlayerSessionRef(playerId, 1L), "first");
        store.put(new PlayerSessionRef(playerId, 2L), "second");
        store.put(new PlayerSessionRef(UUID.randomUUID(), 3L), "other-player");

        store.removePlayer(playerId);

        assertEquals(1, store.size());
    }

    @Test
    void getOrCreateRunsFactoryOncePerSession() {
        PlayerSessionStateStore<String> store = new PlayerSessionStateStore<>();
        PlayerSessionRef session = new PlayerSessionRef(UUID.randomUUID(), 1L);
        AtomicInteger loads = new AtomicInteger();

        store.getOrCreate(session, () -> "value-" + loads.incrementAndGet());
        store.getOrCreate(session, () -> "value-" + loads.incrementAndGet());

        assertEquals(1, loads.get());
    }
}
