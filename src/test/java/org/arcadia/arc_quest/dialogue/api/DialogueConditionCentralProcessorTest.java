package org.arcadia.arc_quest.dialogue.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueConditionCentralProcessorTest {

    @Test
    void logicalConditionsKeepEmptyCollectionSemantics() {
        DialogueCondition all = new DialogueCondition.All(List.of());
        DialogueCondition any = new DialogueCondition.Any(List.of());

        assertTrue(all.test(null));
        assertFalse(any.test(null));
        assertFalse(new DialogueCondition.Not(all).test(null));
    }
}
