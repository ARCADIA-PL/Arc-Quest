package org.arcadia.arc_quest.core.state;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ExpiringStateStore<K, V> {

    private final Map<K, TimedValue<V>> values = new ConcurrentHashMap<>();

    public V put(K key, V value, long expiresAt) {
        TimedValue<V> previous = values.put(key, new TimedValue<>(value, expiresAt));
        return previous != null ? previous.value() : null;
    }

    public boolean putIfAbsent(K key, V value, long expiresAt) {
        return values.putIfAbsent(key, new TimedValue<>(value, expiresAt)) == null;
    }

    public TakeResult<V> take(K key, long now) {
        TimedValue<V> value = values.remove(key);
        if (value == null) return new TakeResult<>(TakeStatus.MISSING, null);
        if (value.expiresAt() < now) return new TakeResult<>(TakeStatus.EXPIRED, value.value());
        return new TakeResult<>(TakeStatus.ACTIVE, value.value());
    }

    public TakeResult<V> get(K key, long now) {
        TimedValue<V> value = values.get(key);
        if (value == null) return new TakeResult<>(TakeStatus.MISSING, null);
        if (value.expiresAt() < now) {
            values.remove(key, value);
            return new TakeResult<>(TakeStatus.EXPIRED, value.value());
        }
        return new TakeResult<>(TakeStatus.ACTIVE, value.value());
    }

    public V remove(K key) {
        TimedValue<V> removed = values.remove(key);
        return removed != null ? removed.value() : null;
    }

    public int cleanupExpired(long now) {
        int removed = 0;
        for (Map.Entry<K, TimedValue<V>> entry : values.entrySet()) {
            TimedValue<V> value = entry.getValue();
            if (value.expiresAt() < now && values.remove(entry.getKey(), value)) {
                removed++;
            }
        }
        return removed;
    }

    public int size() {
        return values.size();
    }

    public void clear() {
        values.clear();
    }

    private record TimedValue<V>(V value, long expiresAt) {
    }

    public enum TakeStatus {
        ACTIVE,
        EXPIRED,
        MISSING
    }

    public record TakeResult<V>(TakeStatus status, V value) {
        public boolean active() {
            return status == TakeStatus.ACTIVE;
        }
    }
}
