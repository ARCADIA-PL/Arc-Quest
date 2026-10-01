package org.arcadia.arc_quest.dialogue.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class DialogueActionSequenceTest {
    @Test
    void failureStopsRewardsAndDoesNotRepeatAlreadyExecutedCost() {
        List<String> effects = new ArrayList<>();
        RuntimeException failure = new IllegalStateException("cost callback failed");
        var result = DialogueActionSequence.execute(List.of("cost", "failure", "reward"), () -> true, action -> {
            effects.add(action);
            if (action.equals("failure")) throw failure;
        });
        assertEquals(List.of("cost", "failure"), effects);
        assertEquals(DialogueActionSequence.Status.FAILED, result.status());
        assertEquals(1, result.actionIndex());
        assertSame(failure, result.failure());
    }

    @Test
    void sessionReplacementDuringActionStopsOldChain() {
        AtomicBoolean current = new AtomicBoolean(true);
        List<String> effects = new ArrayList<>();
        var result = DialogueActionSequence.execute(List.of("replace", "old reward"), current::get, action -> {
            effects.add(action);
            current.set(false);
        });
        assertEquals(List.of("replace"), effects);
        assertEquals(DialogueActionSequence.Status.INTERRUPTED, result.status());
        assertEquals(1, result.actionIndex());
    }

    @Test
    void lastActionEndingSessionAlsoPreventsNodeTransition() {
        AtomicBoolean current = new AtomicBoolean(true);
        var result = DialogueActionSequence.execute(List.of("end"), current::get, action -> current.set(false));
        assertEquals(DialogueActionSequence.Status.INTERRUPTED, result.status());
    }

    @Test
    void alreadyEndedSessionExecutesNothing() {
        var result = DialogueActionSequence.execute(List.of("reward"), () -> false,
                action -> fail("Ended session executed an action"));
        assertEquals(DialogueActionSequence.Status.INTERRUPTED, result.status());
        assertEquals(0, result.actionIndex());
    }

    @Test
    void successfulChainKeepsOrderAndAllowsTransition() {
        List<String> effects = new ArrayList<>();
        var result = DialogueActionSequence.execute(List.of("cost", "reward"), () -> true, effects::add);
        assertEquals(List.of("cost", "reward"), effects);
        assertEquals(DialogueActionSequence.Status.COMPLETED, result.status());
        assertNull(result.failure());
    }
}
