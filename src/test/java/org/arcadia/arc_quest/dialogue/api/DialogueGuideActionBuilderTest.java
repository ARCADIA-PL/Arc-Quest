package org.arcadia.arc_quest.dialogue.api;

import org.arcadia.arc_quest.dialogue.builder.DialogueTreeBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueGuideActionBuilderTest {

    @Test
    void choiceCanOpenGuideAndContinueToNextDialogueNode() {
        DialogueTree tree = DialogueTreeBuilder.create("arc_quest:guide_popup_test")
                .npc(DialogueText.literal("Tester"))
                .node("start")
                .say(DialogueText.literal("Open it"), "open_it")
                .choice("confirm", DialogueText.literal("Confirm"), choice -> choice
                        .unlockGuide("arc_quest:diamond_demo")
                        .openGuide("arc_quest:diamond_demo", 0, false)
                        .goTo("finished"))
                .node("finished")
                .say(DialogueText.literal("Done"), "done")
                .choice("end", DialogueText.literal("End"), DialogueTreeBuilder.ChoiceBuilder::close)
                .build();

        DialogueChoice confirm = tree.getNode("start").choices().getFirst();
        DialogueAction.UnlockGuide unlockAction = assertInstanceOf(
                DialogueAction.UnlockGuide.class, confirm.actions().getFirst());
        DialogueAction.OpenGuide action = assertInstanceOf(
                DialogueAction.OpenGuide.class, confirm.actions().get(1));

        assertEquals("arc_quest:diamond_demo", unlockAction.guideId());
        assertEquals("arc_quest:diamond_demo", action.guideId());
        assertEquals("finished", confirm.nextNodeId());
        assertTrue(tree.validate().isEmpty());
    }
}
