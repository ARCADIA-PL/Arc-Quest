package org.arcadia.arc_quest.dialogue.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DialogueSessionCleanupTest {
    @Test
    void cleanupContinuesAfterFailuresAndRetainsOriginalAndSuppressedErrors() {
        List<String> attempts = new ArrayList<>();
        var first = new IllegalStateException("lease failed");
        var second = new IllegalArgumentException("marker failed");
        RuntimeException failure = DialogueSessionCleanup.run(null, () -> { attempts.add("lease"); throw first; });
        failure = DialogueSessionCleanup.run(failure, () -> { attempts.add("marker"); throw second; });
        failure = DialogueSessionCleanup.run(failure, () -> attempts.add("event"));
        assertSame(first, failure);
        assertArrayEquals(new Throwable[]{second}, failure.getSuppressed());
        assertEquals(List.of("lease", "marker", "event"), attempts);
    }

    @Test
    void repeatedSameFailureDoesNotThrowSelfSuppressionException() {
        var failure = new IllegalStateException("shared failure");
        assertSame(failure, DialogueSessionCleanup.run(failure, () -> { throw failure; }));
        assertEquals(0, failure.getSuppressed().length);
    }
}
