package org.arcadia.arc_quest.dialogue.runtime;

import org.arcadia.arc_quest.dialogue.api.CooldownType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueProgressStoreCooldownTest {

    @Test
    void storeDelegatesCooldownEvaluationToCoreProcessor() {
        DialogueProgressStore store = new DialogueProgressStore();
        ProgressKey key = ProgressKey.ofChoice("dialogue", "node", 0);
        store.recordChoiceSelection(key, 1000L, 100L, 100L);

        var status = store.evaluateCooldown(
                key, CooldownType.SECONDS, 5, 0,
                new DialogueProgressStore.TimeSnapshot(2500L, 200L, 200L));

        assertTrue(status.active());
        assertEquals(3500L, status.remainingRealMillis());
    }

    @Test
    void timeRegressionClearsChoiceRecordOnce() {
        DialogueProgressStore store = new DialogueProgressStore();
        ProgressKey key = ProgressKey.ofChoice("dialogue", "node", 0);
        store.recordChoiceSelection(key, 1000L, 100L, 5000L);

        assertTrue(store.clearIfTimeRegressed(key, 4999L));
        assertFalse(store.clearIfTimeRegressed(key, 4999L));
        assertFalse(store.getChoiceSelection(key).exists());
    }
}
