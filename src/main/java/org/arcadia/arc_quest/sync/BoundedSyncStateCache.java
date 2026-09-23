package org.arcadia.arc_quest.sync;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/** 服务端主线程拥有；只缓存成功提交发送的不可变状态，容量耗尽时逐出最久未使用项。 */
public final class BoundedSyncStateCache<K, S> {
    private final int capacity;
    private final Map<K, S> sent = new LinkedHashMap<>(16, 0.75f, true);

    public BoundedSyncStateCache(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Sync cache capacity must be positive");
        this.capacity = capacity;
    }

    public boolean send(K key, S state, boolean force, Runnable sender) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(sender, "sender");
        if (!force && state.equals(sent.get(key))) return false;
        sender.run();
        sent.put(key, state);
        if (sent.size() > capacity) {
            var iterator = sent.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
        return true;
    }

    public void removeIf(Predicate<K> predicate) { sent.keySet().removeIf(predicate); }

    public void clear() { sent.clear(); }

    int size() { return sent.size(); }
}
