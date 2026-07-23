package org.arcadia.arc_quest.sync;

import org.arcadia.arc_quest.core.identity.PlayerSessionRef;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class BoundedProcessedRequestStore<R> {
    private final int maxRequestsPerSession;
    private final Map<PlayerSessionRef, LinkedHashMap<UUID, R>> resultsBySession = new LinkedHashMap<>();

    public BoundedProcessedRequestStore(int maxRequestsPerSession) {
        if (maxRequestsPerSession <= 0) throw new IllegalArgumentException("maxRequestsPerSession must be positive");
        this.maxRequestsPerSession = maxRequestsPerSession;
    }

    public synchronized ProcessedResult<R> process(PlayerSessionRef sessionRef, UUID requestId, Supplier<R> operation) {
        Objects.requireNonNull(sessionRef, "sessionRef");
        Objects.requireNonNull(operation, "operation");
        if (requestId == null || RequestIdempotencyStore.LEGACY_REQUEST_ID.equals(requestId)) {
            return new ProcessedResult<>(Objects.requireNonNull(operation.get(), "operation result"), false);
        }
        LinkedHashMap<UUID, R> results = resultsBySession.computeIfAbsent(sessionRef, ignored -> new LinkedHashMap<>());
        R existing = results.get(requestId);
        if (existing != null) return new ProcessedResult<>(existing, true);
        R result = Objects.requireNonNull(operation.get(), "operation result");
        results.put(requestId, result);
        while (results.size() > maxRequestsPerSession) {
            Iterator<UUID> iterator = results.keySet().iterator();
            if (!iterator.hasNext()) break;
            iterator.next();
            iterator.remove();
        }
        return new ProcessedResult<>(result, false);
    }

    public synchronized void clearPlayer(UUID playerUuid) {
        resultsBySession.keySet().removeIf(sessionRef -> sessionRef.playerUuid().equals(playerUuid));
    }

    public synchronized void clear() {
        resultsBySession.clear();
    }

    synchronized int trackedRequestCount(PlayerSessionRef sessionRef) {
        Map<UUID, R> results = resultsBySession.get(sessionRef);
        return results != null ? results.size() : 0;
    }

    public record ProcessedResult<R>(R result, boolean replayed) {
    }
}
