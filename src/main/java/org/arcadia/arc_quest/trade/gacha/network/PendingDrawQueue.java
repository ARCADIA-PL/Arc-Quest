package org.arcadia.arc_quest.trade.gacha.network;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** 服务端主线程拥有；超时只触发补发，不删除已付费结果。回调失败后禁止自动重试。 */
final class PendingDrawQueue<T> {
    private enum State { RESERVED, READY, DELIVERING, FAILED }

    private final int capacity;
    private final Map<UUID, Entry<T>> entries = new HashMap<>();

    PendingDrawQueue(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Pending draw capacity must be positive");
        this.capacity = capacity;
    }

    UUID reserve(UUID player) {
        if (entries.containsKey(player) || entries.size() >= capacity) return null;
        UUID token = UUID.randomUUID();
        entries.put(player, new Entry<>(token));
        return token;
    }

    boolean publish(UUID player, UUID token, String shop, T value, long dueAt) {
        Entry<T> entry = entries.get(player);
        if (entry == null || !entry.token.equals(token) || entry.state != State.RESERVED) return false;
        entry.shop = Objects.requireNonNull(shop);
        entry.value = Objects.requireNonNull(value);
        entry.dueAt = dueAt;
        entry.state = State.READY;
        return true;
    }

    void releaseReservation(UUID player, UUID token) {
        Entry<T> entry = entries.get(player);
        if (entry == null || !entry.token.equals(token)) return;
        if (entry.state == State.RESERVED) entries.remove(player);
        else entry.preparing = false;
    }

    void failReservation(UUID player, UUID token) {
        Entry<T> entry = entries.get(player);
        if (entry != null && entry.token.equals(token) && entry.state != State.DELIVERING) entry.state = State.FAILED;
    }

    boolean deliver(UUID player, String expectedShop, Consumer<T> grant) {
        Entry<T> entry = entries.get(player);
        if (entry == null) return false;
        if (entry.state == State.FAILED) throw new IllegalStateException("Unresolved draw failure for player " + player);
        if (entry.state != State.READY || entry.preparing) return false;
        if (expectedShop != null && !expectedShop.equals(entry.shop)) return false;
        entry.state = State.DELIVERING;
        try {
            grant.accept(entry.value);
        } catch (RuntimeException failure) {
            entry.state = State.FAILED;
            throw failure;
        }
        entries.remove(player, entry);
        return true;
    }

    List<UUID> duePlayers(long now) {
        return entries.entrySet().stream()
                .filter(entry -> entry.getValue().state == State.READY && !entry.getValue().preparing
                        && entry.getValue().dueAt <= now)
                .map(Map.Entry::getKey).toList();
    }

    boolean contains(UUID player) { return entries.containsKey(player); }

    UUID token(UUID player) {
        Entry<T> entry = entries.get(player);
        return entry == null ? null : entry.token;
    }

    void discard(UUID player) {
        Entry<T> entry = entries.get(player);
        if (entry != null && !entry.preparing && entry.state != State.DELIVERING) entries.remove(player);
    }

    int size() { return entries.size(); }

    void clear() { entries.clear(); }

    private static final class Entry<T> {
        final UUID token;
        State state = State.RESERVED;
        boolean preparing = true;
        String shop;
        T value;
        long dueAt;

        Entry(UUID token) { this.token = token; }
    }
}
