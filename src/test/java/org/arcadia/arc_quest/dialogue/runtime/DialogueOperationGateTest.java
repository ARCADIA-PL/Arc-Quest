package org.arcadia.arc_quest.dialogue.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DialogueOperationGateTest {
    @Test
    void callbackCannotReenterButReturnHookCanStartNextOperation() {
        DialogueOperationGate gate = new DialogueOperationGate();
        try (var operation = gate.enter()) {
            assertNotNull(operation);
            assertNull(gate.enter());
        }
        try (var next = gate.enter()) {
            assertNotNull(next);
        }
    }

    @Test
    void exceptionReleasesOperationAndOldScopeCannotReleaseSuccessor() {
        DialogueOperationGate gate = new DialogueOperationGate();
        var first = gate.enter();
        assertThrows(IllegalStateException.class, () -> {
            try (first) {
                throw new IllegalStateException("callback failed");
            }
        });
        try (var second = gate.enter()) {
            assertNotNull(second);
            first.close();
            assertNull(gate.enter());
        }
    }
}
