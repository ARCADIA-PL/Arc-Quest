package org.arcadia.arc_quest.questmarker.api;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class MarkTargetResolverRegistry {
    private static final Map<String, MarkTargetResolver> RESOLVERS = new ConcurrentHashMap<>();

    private MarkTargetResolverRegistry() {
    }

    public static void register(String resolverId, MarkTargetResolver resolver) {
        if (resolverId == null || resolverId.isBlank()) {
            throw new IllegalArgumentException("resolverId cannot be blank");
        }
        RESOLVERS.put(resolverId, Objects.requireNonNull(resolver, "resolver"));
    }

    public static void unregister(String resolverId) {
        if (resolverId != null) RESOLVERS.remove(resolverId);
    }

    public static MarkTargetResolver get(String resolverId) {
        return resolverId == null ? null : RESOLVERS.get(resolverId);
    }

    public static Map<String, MarkTargetResolver> snapshot() {
        return Map.copyOf(RESOLVERS);
    }
}
