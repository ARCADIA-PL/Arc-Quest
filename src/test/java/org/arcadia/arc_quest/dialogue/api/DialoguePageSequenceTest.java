package org.arcadia.arc_quest.dialogue.api;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DialoguePageSequenceTest {
    @Test void entryAndLastPageRetainGameplayMetadataAndChoiceIdentity() {
        var choice = new DialogueChoice("accept", DialogueText.literal("Accept the terms"), "done",
                List.of(new DialogueCondition.HasFlag("ready")), List.of(new DialogueAction.Close()),
                false, 60, CooldownType.SECONDS, 100, 5, "restore", null, List.of());
        var original = new DialogueNode("root", DialogueText.literal("Speaker"), DialogueText.literal("Old"),
                Map.of("test", ConditionalSay.of("conditional", DialogueText.literal("Condition"))), List.of(choice),
                "done", 500, false, 60, CooldownType.SECONDS, 100, null, "old_say");
        var options = DialoguePageSequence.Options.defaults("test:tree");
        var nodes = DialoguePageSequence.assemble(original, pages(), options);
        var first = nodes.get("root"); var last = nodes.get("root__page_1");
        assertEquals(original.conditionalTexts(), first.conditionalTexts()); assertEquals("old_say", first.defaultSayId());
        assertEquals(1, first.choices().size()); assertTrue(first.choices().get(0).actions().isEmpty());
        assertNull(first.autoNextId()); assertEquals(0, first.delayMs());
        assertSame(choice, last.choices().get(0)); assertEquals("done", last.autoNextId()); assertEquals(500, last.delayMs());
        assertEquals(original.repeatable(), last.repeatable()); assertEquals(original.cooldownSeconds(), last.cooldownSeconds());
        assertEquals(original.cooldownType(), last.cooldownType()); assertEquals(original.resetTimeTicks(), last.resetTimeTicks());
        assertTrue(last.conditionalTexts().isEmpty());
    }
    @Test void explicitPageSuffixKeepsPublishedSaveAndChoiceAddresses() {
        var original = DialogueNode.builder("entry").text("Old").build();
        var options = new DialoguePageSequence.Options("__story_", "tree/story/", "tree/say/", DialogueText.literal("Continue"),
                DialoguePageSequence.ConditionalTextPolicy.REPLACE);
        var result = DialoguePageSequence.assemble(original, pages(), options);
        assertEquals(Set.of("entry", "entry__story_1"), result.keySet());
        assertEquals("tree/story/entry", result.get("entry").choices().get(0).choiceId());
        assertEquals("entry__story_1", result.get("entry").choices().get(0).nextNodeId());
        assertEquals("tree/say/entry__story_1", result.get("entry__story_1").defaultSayId());
    }
    @Test void collisionAndInvalidSequenceLeaveDestinationUnchanged() {
        var original = DialogueNode.builder("root").text("Old").build();
        var occupied = DialogueNode.builder("root__page_1").text("Unrelated").build();
        var destination = new LinkedHashMap<>(Map.of("root", original, occupied.nodeId(), occupied));
        assertThrows(IllegalArgumentException.class, () -> DialoguePageSequence.install(destination, original, pages(), DialoguePageSequence.Options.defaults("tree")));
        assertSame(original, destination.get("root")); assertSame(occupied, destination.get(occupied.nodeId()));
        assertThrows(IllegalArgumentException.class, () -> DialoguePageSequence.assemble(original, List.of(), DialoguePageSequence.Options.defaults("tree")));
    }
    @Test void textReplacementCannotSilentlyDiscardEconomicChoices() {
        var original = DialogueNode.builder("root").text("Old")
                .choices(DialogueChoice.withAction("claim", DialogueText.literal("Claim"), null, new DialogueAction.Close())).build();
        var page = new DialoguePageSequence.Page(null, DialogueText.literal("Replacement"), List.of());
        assertThrows(IllegalArgumentException.class, () -> DialoguePageSequence.assemble(original, List.of(page), DialoguePageSequence.Options.defaults("tree")));
    }
    private static List<DialoguePageSequence.Page> pages() {
        return List.of(new DialoguePageSequence.Page(null, DialogueText.literal("First")), new DialoguePageSequence.Page(null, DialogueText.literal("Second")));
    }
}
