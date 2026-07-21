package org.arcadia.arc_quest.core.event;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class ListenerRegistry<L> {

    private final CopyOnWriteArrayList<L> listeners = new CopyOnWriteArrayList<>();

    public boolean subscribe(L listener) {
        Objects.requireNonNull(listener, "listener");
        return listeners.addIfAbsent(listener);
    }

    public boolean unsubscribe(L listener) {
        return listeners.remove(listener);
    }

    public void clear() {
        listeners.clear();
    }

    public int size() {
        return listeners.size();
    }

    public List<L> snapshot() {
        return List.copyOf(listeners);
    }

    public void dispatch(Consumer<? super L> invocation,
                         BiConsumer<? super L, ? super RuntimeException> failureHandler) {
        Objects.requireNonNull(invocation, "invocation");
        Objects.requireNonNull(failureHandler, "failureHandler");
        for (L listener : listeners) {
            try {
                invocation.accept(listener);
            } catch (RuntimeException exception) {
                failureHandler.accept(listener, exception);
            }
        }
    }
}
