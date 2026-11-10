package org.arcadia.arc_quest.dialogue.api;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.arcadia.arc_quest.dialogue.builder.DialogueTreeBuilder;
import static org.junit.jupiter.api.Assertions.*;

class AutomaticDialogueChoiceTest {
    @Test void automaticContinuationRetainsItsChoiceOnlyOnTheFinalDeclaredPage() {
        var tree = DialogueTreeBuilder.create("test:auto").node("root")
                .pages("test:say/root", new DialoguePageSequence.Options("__story_", "test:page/", "test:say/",
                        DialogueText.literal("continue"), DialoguePageSequence.ConditionalTextPolicy.REPLACE, 800),
                        new DialoguePageSequence.Page(DialogueText.literal("npc"), DialogueText.literal("first")),
                        new DialoguePageSequence.Page(DialogueText.literal("player"), DialogueText.literal("last")))
                .automaticChoice("test:accept")
                .choice("test:accept", DialogueText.literal("accept"), choice -> choice
                        .onlyIf(new DialogueCondition.HasFlag("ready")).setFlag("accepted").close())
                .build();
        assertNull(tree.getNode("root").automaticChoiceId());
        var last = tree.getNode("root__story_1");
        assertEquals("test:accept", last.automaticChoiceId());
        assertFalse(last.isTerminal());
        assertEquals(1, last.choices().size());
        assertEquals(2, last.choices().get(0).actions().size());
        assertEquals(List.of(new DialogueCondition.HasFlag("ready")), last.choices().get(0).conditions());
    }

    @Test void automaticDefinitionRejectsAmbiguousMissingOrConflictingRoutes() {
        var choice = DialogueChoice.of("accept", DialogueText.literal("yes"), "done");
        assertThrows(IllegalArgumentException.class, () -> DialogueNode.builder("root").automaticChoice("accept").build());
        assertThrows(IllegalArgumentException.class, () -> DialogueNode.builder("root").choices(choice, choice).automaticChoice("accept").build());
        assertThrows(IllegalArgumentException.class, () -> DialogueNode.builder("root").choices(choice).automaticChoice("different").build());
        assertThrows(IllegalArgumentException.class, () -> DialogueNode.builder("root").choices(choice).automaticChoice("accept").autoNext("done").build());
    }

    @Test void legacyConstructorsKeepManualChoiceBehavior() {
        var node = new DialogueNode("root", null, null, Map.of(), List.of(), null, 0, true, 0,
                CooldownType.NONE, 0, null, null);
        assertNull(node.automaticChoiceId());
        assertTrue(node.isTerminal());
    }
}
