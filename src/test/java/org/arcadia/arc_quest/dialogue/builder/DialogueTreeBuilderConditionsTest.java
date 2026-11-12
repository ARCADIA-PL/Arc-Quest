package org.arcadia.arc_quest.dialogue.builder;

import org.arcadia.arc_quest.dialogue.api.DialogueCondition;
import org.arcadia.arc_quest.dialogue.api.DialogueText;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DialogueTreeBuilderConditionsTest {
    @Test void equalPriorityTextsKeepDeclarationOrderAndCannotBeMutated() {
        var tree = DialogueTreeBuilder.create("test:order").node("root")
                .sayIf(new DialogueCondition.HasFlag("test:b"), "first", 10, "test:first")
                .sayIf(new DialogueCondition.HasFlag("test:a"), "second", 10, "test:second").build();
        var texts = tree.nodes().get("root").conditionalTexts();
        assertEquals(List.of("test:first", "test:second"), texts.values().stream().map(s -> s.sayId()).toList());
        assertThrows(UnsupportedOperationException.class, texts::clear);
    }

    @Test void phaseAndFailedConditionsHaveExplicitKeys() {
        assertEquals("QUEST_FAILED:test:q", org.arcadia.arc_quest.condition.ConditionBridge.serializeConditionKey(new DialogueCondition.QuestFailed("test:q")));
        assertEquals("QUEST_PHASE_COMPLETED:test:q|test:p", org.arcadia.arc_quest.condition.ConditionBridge.serializeConditionKey(new DialogueCondition.QuestPhaseCompleted("test:q", "test:p")));
        assertEquals("PHASE_BETWEEN:test:q|test:a|test:b", org.arcadia.arc_quest.condition.ConditionBridge.serializeConditionKey(new DialogueCondition.PhaseBetween("test:q", "test:a", "test:b")));
        assertThrows(IllegalArgumentException.class, () -> org.arcadia.arc_quest.condition.ConditionBridge.serializeConditionKey(new DialogueCondition.NodeVisited("test:node")));
    }
    @Test void nativeShopChoiceResolvesCurrentNodeBeforeOpening() {
        var tree = DialogueTreeBuilder.create("test:shop").node("root")
                .choice("shop", DialogueText.literal("shop"), choice -> choice.openTrade("test:shop"))
                .build();
        var choice = tree.nodes().get("root").choices().get(0);
        assertEquals("root", choice.restoreNodeId());
        assertEquals("root", ((org.arcadia.arc_quest.dialogue.api.DialogueAction.OpenTrade) choice.actions().get(0)).restoreNodeId());
    }
    @Test void translatedPriorityAndNestedBranchBoundariesSurviveConstruction() {
        var condition = new DialogueCondition.Any(List.of(
                new DialogueCondition.All(List.of(new DialogueCondition.HasFlag("test:a"),
                        new DialogueCondition.Not(new DialogueCondition.HasFlag("test:b")))),
                new DialogueCondition.HasFlag("test:c")));
        var tree = DialogueTreeBuilder.create("test:dialogue").node("root")
                .say(DialogueText.literal("fallback"), "test:fallback")
                .sayIf(condition, DialogueText.translatable("test.translated"), 200, "test:branch")
                .build();
        var texts = tree.nodes().get("root").conditionalTexts();
        assertTrue(texts.containsKey("200|ANY:(ALL:(HAS_FLAG:test:a);(NOT:HAS_FLAG:test:b));(HAS_FLAG:test:c)"));
        assertEquals("test:branch", texts.values().iterator().next().sayId());
    }

    @Test void duplicatesAndDanglingEdgesFailBeforeRegistration() {
        assertThrows(IllegalStateException.class, () -> DialogueTreeBuilder.create("test:duplicate")
                .node("root").node("root").build());
        assertThrows(IllegalStateException.class, () -> DialogueTreeBuilder.create("test:duplicate_condition")
                .node("root").sayIf(new DialogueCondition.HasFlag("test:a"), "a", "test:a")
                .sayIf(new DialogueCondition.HasFlag("test:a"), "b", "test:b").build());
        assertThrows(IllegalStateException.class, () -> DialogueTreeBuilder.create("test:dangling")
                .node("root").choice("next", DialogueText.literal("next"), "missing").build());
    }
}
