package org.arcadia.arc_quest.questplayer;

import org.arcadia.arc_quest.core.identity.PlayerSessionRef;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

final class PlayerSessionStateStore<T> {

    private final ConcurrentHashMap<PlayerSessionRef, T> values = new ConcurrentHashMap<>();

    @Nullable
    T get(PlayerSessionRef session) {
        return values.get(session);
    }

    T getOrCreate(PlayerSessionRef session, Supplier<T> factory) {
        return values.computeIfAbsent(session, ignored -> factory.get());
    }

    void put(PlayerSessionRef session, T value) {
        values.put(session, value);
    }

    @Nullable
    T remove(PlayerSessionRef session) {
        return values.remove(session);
    }

    void removePlayer(java.util.UUID playerUuid) {
        values.keySet().removeIf(session -> session.playerUuid().equals(playerUuid));
    }

    void clear() {
        values.clear();
    }

    int size() {
        return values.size();
    }
}
