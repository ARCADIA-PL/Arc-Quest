package org.arcadia.arc_quest.dialogue.runtime;

import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.dialogue.api.DialogueText;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class DialogueSessionComponentTest {

    @Test
    void keepsDefaultDialogueTextUnresolvedDuringConditionalSelection() {
        DialogueText original = DialogueText.translatable("arc_quest.test.dialogue");

        ConditionalTextEvaluator.DialogueTextSelection selection =
                ConditionalTextEvaluator.evaluateDialogueWithIndex(null, Map.of(), original);

        assertSame(original, selection.text());
    }

    @Test
    void keepsLegacyNullDefaultTextBehavior() {
        ConditionalTextEvaluator.SayIfResult result =
                ConditionalTextEvaluator.evaluateWithIndex(null, Map.of(), null);

        assertNull(result.text);
    }

    @Test
    void preservesTranslatableComponentWhenLegacyProcessingDoesNotChangeText() {
        Component original = Component.translatable("arc_quest.test.dialogue", "Player");

        Component processed = DialogueSession.preserveComponentUnlessChanged(
                original, text -> text);

        assertSame(original, processed);
    }

    @Test
    void appliesLegacyPlaceholderProcessingWhenTextChanges() {
        Component original = Component.literal("Hello, %player%!");

        Component processed = DialogueSession.preserveComponentUnlessChanged(
                original, text -> text.replace("%player%", "May"));

        assertNotSame(original, processed);
        assertEquals("Hello, May!", processed.getString());
    }
}
