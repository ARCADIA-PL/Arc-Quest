package org.arcadia.arc_quest.client.hud.quest.icon.portrait;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/** Render-thread LRU; eviction and clear release owned resources exactly once. */
final class BoundedPortraitCache<K, V> {
    private final int limit;
    private final Consumer<V> release;
    private final Map<K, V> entries = new LinkedHashMap<>(16, 0.75f, true);

    BoundedPortraitCache(int limit, Consumer<V> release) {
        if (limit <= 0) throw new IllegalArgumentException("Portrait cache limit must be positive");
        this.limit = limit;
        this.release = release;
    }

    V get(K key) { return entries.get(key); }

    V computeIfAbsent(K key, Function<K, V> factory) {
        V existing = entries.get(key);
        if (existing != null) return existing;
        V created = factory.apply(key);
        entries.put(key, created);
        if (entries.size() > limit) {
            var first = entries.entrySet().iterator();
            V oldest = first.next().getValue();
            first.remove();
            release.accept(oldest);
        }
        return created;
    }

    void clear() {
        entries.values().forEach(release);
        entries.clear();
    }

    int size() { return entries.size(); }
}
