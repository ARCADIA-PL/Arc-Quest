package org.arcadia.arc_quest.dialogue.runtime;

import org.arcadia.arc_quest.dialogue.api.*;
import org.arcadia.arc_quest.dialogue.builder.DialogueTreeBuilder;
import org.arcadia.arc_quest.dialogue.registry.DialogueRegistry;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DialogueIdentityMigrationTest {
    private DialogueTree tree() {
        return DialogueTreeBuilder.create("test:merged")
                .legacyTree("test:retired", "old_root", Map.of("root", "old_root", "root__story_1", "old_page"))
                .node("root").say(DialogueText.literal("default"))
                .node("old_root").say(DialogueText.literal("old"))
                .node("old_page").say(DialogueText.literal("page")).build();
    }

    @Test void oldDirectEntryIsAViewAndDoesNotIncreaseRegistryCount() {
        var tree = tree();
        int previous = DialogueRegistry.INSTANCE.size();
        DialogueRegistry.INSTANCE.register(tree);
        assertEquals(previous + 1, DialogueRegistry.INSTANCE.size());
        var old = DialogueRegistry.INSTANCE.get("test:retired");
        assertNotNull(old);
        assertEquals("test:merged", old.dialogueId());
        assertEquals("old_root", old.startNodeId());
        assertSame(tree.nodes(), old.nodes());
        assertFalse(DialogueRegistry.INSTANCE.getAllIds().contains("test:retired"));
        assertEquals("old_page", DialogueRegistry.INSTANCE.resolveNodeId("test:retired", "root__story_1"));
        DialogueRegistry.INSTANCE.registerDatapack(DialogueTreeBuilder.create("test:retired")
                .node("stale").say(DialogueText.literal("stale datapack")).build());
        assertEquals("test:merged", DialogueRegistry.INSTANCE.get("test:retired").dialogueId());
        assertEquals(previous + 1, DialogueRegistry.INSTANCE.size());
        assertThrows(IllegalStateException.class, () -> DialogueRegistry.INSTANCE.register(
                DialogueTreeBuilder.create("test:retired").node("root").say(DialogueText.literal("duplicate")).build()));
    }

    @Test void visitsChoicesCompletionAndNewestClockSurviveReloadWithoutRegranting() {
        var progress = new DialogueProgressStore();
        progress.recordNodeVisit("test:retired", "root__story_1", 10, 20, 30);
        progress.recordChoiceSelection("test:retired", "root", 11, 11, 21, 31);
        progress.recordDialogueVisit("test:retired", "test:retired", 12, 22, 32);
        progress.recordDialogueCompletion("test:retired", "test:retired");
        progress.recordNodeVisit("test:merged", "old_page", 15, 25, 35);
        progress.migrateIdentities(tree(), "test:merged");
        assertEquals(15, progress.getNodeVisit("test:merged", "old_page").realTime());
        assertEquals(11, progress.getChoiceSelection("test:merged", "old_root", 11).realTime());
        assertEquals(12, progress.getDialogueVisit("test:merged", "test:merged").realTime());
        assertTrue(progress.hasCompletedDialogue("test:merged", "test:merged"));
        assertTrue(progress.hasVisitedNode("test:retired", "root__story_1"));
        var saved = progress.serialize();
        var restored = new DialogueProgressStore();
        restored.deserialize(saved);
        restored.migrateIdentities(tree(), "test:merged");
        assertFalse(restored.isDirty());
        assertEquals(saved, restored.serialize());
    }

    @Test void instanceScopeOnlyMovesDeclaredNodesAndDoesNotConsumeUnvisitedTrees() {
        var progress = new DialogueProgressStore();
        progress.recordNodeVisit("npc-uuid", "root__story_1", 3, 4, 5);
        progress.recordChoiceSelection("npc-uuid", "root", 1, 6, 7, 8);
        progress.recordNodeVisit("other-uuid", "root__story_1", 9, 10, 11);
        progress.migrateIdentities(tree(), "npc-uuid");
        assertTrue(progress.hasVisitedNode("npc-uuid", "old_page"));
        assertTrue(progress.hasSelectedChoice("npc-uuid", "old_root", 1));
        assertFalse(progress.hasVisitedNode("other-uuid", "old_page"));
        assertFalse(progress.hasCompletedDialogue("npc-uuid", "test:merged"));
    }

    @Test void originalSubgraphControlsResultAndReturnTargets() {
        var tree = tree();
        assertEquals("old_root", tree.getNodeFromEntry("old_root", "root").nodeId());
        assertEquals("old_page", tree.getNodeFromEntry("old_root", "root__story_1").nodeId());
        assertNull(tree.getNodeFromEntry("old_root", "unrelated_batch_result"));
        assertEquals("test:retired", tree.sourceDialogueIdForEntry("old_root"));
        assertEquals("test:merged", tree.sourceDialogueIdForEntry("root"));
        assertEquals("root", tree.getNodeFromEntry("root", "root").nodeId());
    }

    @Test void invalidTargetsFailAtDefinitionTime() {
        assertThrows(IllegalStateException.class, () -> DialogueTreeBuilder.create("test:invalid")
                .legacyTree("test:old-invalid", "missing", Map.of("root", "missing"))
                .node("root").say(DialogueText.literal("x")).build());
    }
}
