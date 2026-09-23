package org.arcadia.arc_quest.dialogue.runtime;

import org.arcadia.arc_quest.dialogue.api.DialogueChoice;
import org.arcadia.arc_quest.dialogue.api.DialogueText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DialogueChoiceSelectionTest {
    @Test
    void equalChoicesKeepSeparateOriginalIndices() {
        var choice = DialogueChoice.of("same", DialogueText.literal("same"), "next");
        var selection = DialogueChoiceSelection.select(List.of(choice, choice), ignored -> true, () -> true);
        assertEquals(List.of(choice, choice), selection.choices());
        assertArrayEquals(new int[]{0, 1}, selection.originalIndices());
    }

    @Test
    void priorityFilteringKeepsDeclarationOrderAndIndices() {
        var low = DialogueChoice.prioritized("low", DialogueText.literal("low"), "next", -10);
        var high = DialogueChoice.prioritized("high", DialogueText.literal("high"), "next", -1);
        var hidden = DialogueChoice.prioritized("hidden", DialogueText.literal("hidden"), "next", 100);
        var selection = DialogueChoiceSelection.select(List.of(low, high, hidden, high), c -> c != hidden, () -> true);
        assertEquals(List.of(high, high), selection.choices());
        assertArrayEquals(new int[]{1, 3}, selection.originalIndices());
    }

    @Test
    void endedSessionStopsEvaluatingFurtherExtensionConditions() {
        AtomicBoolean current = new AtomicBoolean(true);
        AtomicInteger calls = new AtomicInteger();
        var choice = DialogueChoice.of("choice", DialogueText.literal("choice"), "next");
        DialogueChoiceSelection.select(List.of(choice, choice), ignored -> {
            calls.incrementAndGet();
            current.set(false);
            return true;
        }, current::get);
        assertEquals(1, calls.get());
    }
}
