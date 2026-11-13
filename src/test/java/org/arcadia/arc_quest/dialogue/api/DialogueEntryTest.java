package org.arcadia.arc_quest.dialogue.api;

import java.util.List;
import org.arcadia.arc_quest.dialogue.builder.DialogueTreeBuilder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DialogueEntryTest {
    private static final DialogueCondition YES = new DialogueCondition.All(List.of());
    private static final DialogueCondition NO = new DialogueCondition.Not(YES);

    @Test void orderedMatchingEntriesUseExistingNodesWithoutMutatingTheFallback() {
        var tree = DialogueTreeBuilder.create("test:entry").startNode("root")
                .entryIf("skipped", NO).entryIf("report", YES).entryIf("later", YES)
                .node("root").node("skipped").node("report").node("later").build();
        assertEquals("report", tree.resolveStartNode(null).nodeId());
        assertEquals("root", tree.getStartNode().nodeId());
        assertEquals("report", tree.resolveStartNode(null).nodeId());
        assertThrows(UnsupportedOperationException.class, () -> tree.entries().clear());
    }

    @Test void noMatchingEntryPreservesLegacyStartAndMissingTargetsFailValidation() {
        var tree = DialogueTreeBuilder.create("test:fallback").startNode("root")
                .entryIf("other", NO).node("root").node("other").build();
        assertEquals("root", tree.resolveStartNode(null).nodeId());
        assertThrows(IllegalStateException.class, () -> DialogueTreeBuilder.create("test:missing")
                .entryIf("missing", YES).node("root").build());
        var legacy = new DialogueTree(tree.dialogueId(), tree.defaultNpc(), tree.startNodeId(),
                tree.nodes(), tree.visualConfig(), tree.repeatable(), tree.cooldownSeconds(),
                tree.cooldownType(), tree.resetTimeTicks(), tree.relatedMarks());
        assertTrue(legacy.entries().isEmpty());
        assertEquals("root", legacy.resolveStartNode(null).nodeId());
    }
}
