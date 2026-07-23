package org.arcadia.arc_quest.client.hud.quest.splash;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

final class SplashSequenceQueue<T> {

    private final int maxPending;
    private final Deque<T> pending = new ArrayDeque<>();
    @Nullable
    private T active;

    SplashSequenceQueue(int maxPending) {
        if (maxPending < 1) throw new IllegalArgumentException("maxPending must be positive");
        this.maxPending = maxPending;
    }

    boolean enqueue(T value) {
        Objects.requireNonNull(value, "value");
        if (active == null) {
            active = value;
            return true;
        }
        if (pending.size() >= maxPending) return false;
        pending.addLast(value);
        return true;
    }

    @Nullable
    T current() {
        return active;
    }

    @Nullable
    T completeActive() {
        active = pending.pollFirst();
        return active;
    }

    boolean isActive() {
        return active != null;
    }

    int pendingCount() {
        return pending.size();
    }

    void clear() {
        active = null;
        pending.clear();
    }
}
