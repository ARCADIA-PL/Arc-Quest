package org.arcadia.arc_quest.client.quest.tracking;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestTrackingPresentationStateTest {
    @Test void samePhaseEchoPreservesAnExplicitSpecimenAndProgressUpdates() {
        var state = new QuestTrackingPresentationState();
        state.focusCollection("quest", "field", "iron", "run-1");
        state.applyPhaseFocusSync("quest", "field");
        state.bindCollectionRun("quest", "run-1");
        assertEquals("iron", state.collectionBindingIdFor("quest"));
        assertEquals("field", state.phaseIdFor("quest"));
        state.focusCollection("quest", "field", "gold", "run-1");
        state.applyPhaseFocusSync(" quest ", " field ");
        assertEquals("gold", state.collectionBindingIdFor("quest"));
    }

    @Test void changingTheRealPhaseOrQuestCannotReuseTheSameNamedBinding() {
        var state = new QuestTrackingPresentationState();
        state.focusCollection("quest", "field", "iron", "run-1");
        state.applyPhaseFocusSync("quest", "mine");
        assertNull(state.collectionBindingIdFor("quest"));
        assertEquals("mine", state.phaseIdFor("quest"));
        state.focusCollection("quest", "field", "iron", "run-1");
        state.onTrackedQuestChanged("another-quest");
        assertNull(state.collectionBindingIdFor("quest"));
        assertNull(state.phaseIdFor("quest"));
    }

    @Test void repeatRunsClearSelectionEvenWhenQuestPhaseAndBindingIdsMatch() {
        var state = new QuestTrackingPresentationState();
        state.focusCollection("quest", "field", "iron", "run-1");
        state.bindCollectionRun("quest", "run-2");
        assertNull(state.collectionBindingIdFor("quest"));
        assertNull(state.phaseIdFor("quest"));
        state.focusCollection("quest", "field", "gold", "run-2");
        state.bindCollectionRun("another-quest", "another-run");
        assertEquals("gold", state.collectionBindingIdFor("quest"));
    }

    @Test void endingACompletedFocusAndUntrackingHaveSeparateLifetimes() {
        var state = new QuestTrackingPresentationState();
        state.focusCollection("quest", "field", "iron", "run-1");
        state.clearCollectionFocus("another-quest");
        assertEquals("iron", state.collectionBindingIdFor("quest"));
        state.clearCollectionFocus("quest");
        assertNull(state.collectionBindingIdFor("quest"));
        assertEquals("field", state.phaseIdFor("quest"));
        state.focusCollection("quest", "field", "gold", "run-1");
        state.onTrackedQuestChanged(null);
        assertNull(state.collectionBindingIdFor("quest"));
        assertNull(state.phaseIdFor("quest"));
        state.focus(null, "field");
        assertNull(state.phaseIdFor(null));
    }
}
