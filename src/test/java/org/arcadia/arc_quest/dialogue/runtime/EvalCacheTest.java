package org.arcadia.arc_quest.dialogue.runtime;

import org.arcadia.arc_quest.dialogue.api.DialogueCondition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EvalCacheTest {
    private final DialogueCondition condition = new DialogueCondition.HasFlag("test");

    @AfterEach
    void clearCache() {
        EvalCache.clearForTesting();
    }

    @Test
    void nestedCycleDoesNotReuseOrClearOuterResults() {
        EvalCache cache = EvalCache.current();
        cache.beginCycle();
        try {
            assertTrue(cache.computeIfAbsent(condition, () -> true));
            cache.beginCycle();
            try {
                assertFalse(cache.computeIfAbsent(condition, () -> false));
                assertEquals(1, cache.getCacheSize());
            } finally {
                cache.endCycle();
            }
            assertTrue(cache.isInCycle());
            assertTrue(cache.computeIfAbsent(condition, () -> fail("Outer cached value was lost")));
        } finally {
            cache.endCycle();
        }
        assertFalse(cache.isInCycle());
        assertEquals(0, cache.getCacheSize());
    }

    @Test
    void nestedCycleInsideEvaluatorDoesNotCorruptMapComputation() {
        EvalCache cache = EvalCache.current();
        cache.beginCycle();
        try {
            assertTrue(cache.computeIfAbsent(condition, () -> {
                cache.beginCycle();
                try {
                    assertFalse(cache.computeIfAbsent(condition, () -> false));
                    return true;
                } finally {
                    cache.endCycle();
                }
            }));
            assertTrue(cache.computeIfAbsent(condition, () -> fail("Result was not cached")));
        } finally {
            cache.endCycle();
        }
    }

    @Test
    void evaluatorMayCacheOtherConditionsWithoutConcurrentModification() {
        EvalCache cache = EvalCache.current();
        cache.beginCycle();
        try {
            var other = new DialogueCondition.HasFlag("other");
            assertTrue(cache.computeIfAbsent(condition, () -> cache.computeIfAbsent(other, () -> true)));
            assertEquals(2, cache.getCacheSize());
        } finally {
            cache.endCycle();
        }
    }

    @Test
    void failedNestedEvaluationStillLeavesOuterCycleUsable() {
        EvalCache cache = EvalCache.current();
        cache.beginCycle();
        try {
            assertThrows(IllegalStateException.class, () -> {
                cache.beginCycle();
                try {
                    cache.computeIfAbsent(condition, () -> { throw new IllegalStateException("condition failed"); });
                } finally {
                    cache.endCycle();
                }
            });
            assertTrue(cache.computeIfAbsent(condition, () -> true));
        } finally {
            cache.endCycle();
        }
    }
}
