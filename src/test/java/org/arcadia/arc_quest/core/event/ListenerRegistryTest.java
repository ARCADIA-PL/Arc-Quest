package org.arcadia.arc_quest.core.event;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListenerRegistryTest {

    @Test
    void subscriptionIsUniqueAndCanBeRemoved() {
        ListenerRegistry<Runnable> registry = new ListenerRegistry<>();
        Runnable listener = () -> {
        };

        assertTrue(registry.subscribe(listener));
        assertFalse(registry.subscribe(listener));
        assertEquals(1, registry.size());
        assertTrue(registry.unsubscribe(listener));
        assertEquals(0, registry.size());
    }

    @Test
    void listenerFailureDoesNotBlockRemainingListeners() {
        ListenerRegistry<Runnable> registry = new ListenerRegistry<>();
        AtomicInteger invocations = new AtomicInteger();
        List<RuntimeException> failures = new ArrayList<>();
        registry.subscribe(() -> {
            throw new IllegalStateException("failure");
        });
        registry.subscribe(invocations::incrementAndGet);

        registry.dispatch(Runnable::run, (listener, exception) -> failures.add(exception));

        assertEquals(1, invocations.get());
        assertEquals(1, failures.size());
    }

    @Test
    void snapshotIsImmutableAndClearRemovesListeners() {
        ListenerRegistry<Runnable> registry = new ListenerRegistry<>();
        registry.subscribe(() -> {
        });

        List<Runnable> snapshot = registry.snapshot();
        registry.clear();

        assertEquals(1, snapshot.size());
        assertEquals(0, registry.size());
    }
}
