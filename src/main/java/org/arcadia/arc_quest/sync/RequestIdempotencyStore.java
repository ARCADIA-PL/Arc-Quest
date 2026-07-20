package org.arcadia.arc_quest.sync;

import org.arcadia.arc_quest.core.identity.PlayerSessionRef;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class RequestIdempotencyStore {

    public static final RequestIdempotencyStore INSTANCE = new RequestIdempotencyStore(256);
    public static final UUID LEGACY_REQUEST_ID = new UUID(0L, 0L);

    private final int maxRequestsPerSession;
    private final Map<PlayerSessionRef, LinkedHashMap<UUID, Boolean>> requestsBySession = new LinkedHashMap<>();

    RequestIdempotencyStore(int maxRequestsPerSession) {
        if (maxRequestsPerSession <= 0) {
            throw new IllegalArgumentException("maxRequestsPerSession must be positive");
        }
        this.maxRequestsPerSession = maxRequestsPerSession;
    }

    public synchronized boolean claim(PlayerSessionRef sessionRef, UUID requestId) {
        if (requestId == null || LEGACY_REQUEST_ID.equals(requestId)) {
            return true;
        }

        LinkedHashMap<UUID, Boolean> requests = requestsBySession.computeIfAbsent(
                sessionRef, ignored -> new LinkedHashMap<>());
        if (requests.putIfAbsent(requestId, Boolean.TRUE) != null) {
            return false;
        }

        while (requests.size() > maxRequestsPerSession) {
            Iterator<UUID> iterator = requests.keySet().iterator();
            if (!iterator.hasNext()) break;
            iterator.next();
            iterator.remove();
        }
        return true;
    }

    public synchronized void clearPlayer(UUID playerUuid) {
        requestsBySession.keySet().removeIf(sessionRef -> sessionRef.playerUuid().equals(playerUuid));
    }

    public synchronized void clear() {
        requestsBySession.clear();
    }

    synchronized int trackedRequestCount(PlayerSessionRef sessionRef) {
        Map<UUID, Boolean> requests = requestsBySession.get(sessionRef);
        return requests != null ? requests.size() : 0;
    }
}
