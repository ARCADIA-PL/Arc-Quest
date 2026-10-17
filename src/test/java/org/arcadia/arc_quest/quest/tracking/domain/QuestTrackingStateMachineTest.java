package org.arcadia.arc_quest.quest.tracking.domain;

import org.arcadia.arc_quest.quest.tracking.api.QuestTrackingChangeReason;
import org.arcadia.arc_quest.quest.tracking.api.QuestTrackingSnapshot;
import org.arcadia.arc_quest.quest.tracking.api.QuestTrackingState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuestTrackingStateMachineTest {
    private static final String OLD = "test:previous_mainline", MAIN = "test:next_mainline";
    private static final String SIDE = "test:blacksmith", OTHER = "test:other_side";
    private final QuestTrackingStateMachine machine = new QuestTrackingStateMachine();

    @Test void delayedSuccessorReplacesAutomaticSideQuestFallback() {
        var previous = snapshot(OLD, QuestTrackingState.TRACKING_MANUAL);
        var fallback = machine.transition(previous, terminated(), List.of(SIDE), List.of(SIDE)).target();
        assertEquals(SIDE, fallback.questId());
        assertEquals(QuestTrackingState.TRACKING_AUTOMATIC, fallback.state());

        var successor = machine.transition(fallback, accepted(MAIN),
                List.of(SIDE, MAIN), List.of(MAIN, SIDE)).target();
        assertEquals(MAIN, successor.questId());
        assertEquals(QuestTrackingState.TRACKING_AUTOMATIC, successor.state());
    }

    @Test void successorAcceptedBeforePreviousChapterEndsIsSelectedOnTermination() {
        var previous = snapshot(OLD, QuestTrackingState.TRACKING_MANUAL);
        assertEquals(previous, machine.transition(previous, accepted(MAIN),
                List.of(OLD, SIDE, MAIN), List.of(OLD, MAIN, SIDE)).target());
        assertEquals(MAIN, machine.transition(previous, terminated(),
                List.of(SIDE, MAIN), List.of(MAIN, SIDE)).target().questId());
    }

    @Test void mainlinePhaseChangeAndSideQuestAcceptancePreserveManualMainline() {
        var main = snapshot(MAIN, QuestTrackingState.TRACKING_MANUAL);
        assertEquals(main, machine.transition(main, accepted(SIDE),
                List.of(MAIN, SIDE), List.of(MAIN, SIDE)).target());
        assertEquals(main, machine.transition(main, reconcile(),
                List.of(MAIN, SIDE), List.of(MAIN, SIDE)).target());
    }

    @Test void manuallySelectedSideQuestIsNeverReplacedByMainlineAcceptanceOrLogin() {
        var side = snapshot(SIDE, QuestTrackingState.TRACKING_MANUAL);
        assertEquals(side, machine.transition(side, accepted(MAIN),
                List.of(MAIN, SIDE), List.of(MAIN, SIDE)).target());
        assertEquals(side, machine.transition(side, reconcile(),
                List.of(MAIN, SIDE), List.of(MAIN, SIDE)).target());
    }

    @Test void loginRepairsPersistedAutomaticFallbackWhenMainlineIsAlreadyActive() {
        var side = snapshot(SIDE, QuestTrackingState.TRACKING_AUTOMATIC);
        assertEquals(MAIN, machine.transition(side, reconcile(),
                List.of(SIDE, MAIN), List.of(MAIN, SIDE)).target().questId());
    }

    @Test void lowerPriorityAcceptanceDoesNotDisplaceAutomaticMainline() {
        var main = snapshot(MAIN, QuestTrackingState.TRACKING_AUTOMATIC);
        assertEquals(main, machine.transition(main, accepted(SIDE),
                List.of(MAIN, SIDE), List.of(MAIN, SIDE)).target());
    }

    @Test void disabledAutoTrackingCannotBecomeAnAutomaticSuccessor() {
        var side = snapshot(SIDE, QuestTrackingState.TRACKING_AUTOMATIC);
        assertEquals(side, machine.transition(side, accepted(MAIN),
                List.of(SIDE, MAIN), List.of(SIDE)).target());
    }

    @Test void automaticSelectionUsesHighestPriorityRatherThanMostRecentAcceptance() {
        var side = snapshot(SIDE, QuestTrackingState.TRACKING_AUTOMATIC);
        assertEquals(MAIN, machine.transition(side, accepted(OTHER),
                List.of(SIDE, MAIN, OTHER), List.of(MAIN, OTHER, SIDE)).target().questId());
    }

    @Test void manualUntrackingSurvivesTerminationAndLogin() {
        var untracked = snapshot(null, QuestTrackingState.UNTRACKED_MANUAL);
        assertEquals(untracked, machine.transition(untracked, terminated(),
                List.of(MAIN, SIDE), List.of(MAIN, SIDE)).target());
        assertEquals(untracked, machine.transition(untracked, reconcile(),
                List.of(MAIN, SIDE), List.of(MAIN, SIDE)).target());
    }

    @Test void existingActiveSelectionExcludedFromAutomaticCandidatesIsRetained() {
        var side = snapshot(SIDE, QuestTrackingState.TRACKING_AUTOMATIC);
        assertEquals(side, machine.transition(side, accepted(MAIN),
                List.of(SIDE, MAIN), List.of(MAIN)).target());
    }

    @Test void missingAcceptedQuestDoesNotChangeExistingTracking() {
        var side = snapshot(SIDE, QuestTrackingState.TRACKING_AUTOMATIC);
        assertEquals(side, machine.transition(side, accepted(null),
                List.of(SIDE, MAIN), List.of(MAIN, SIDE)).target());
    }

    private static QuestTrackingSnapshot snapshot(String quest, QuestTrackingState state) {
        return new QuestTrackingSnapshot(quest, state, 9);
    }
    private static QuestTrackingAction accepted(String quest) {
        return QuestTrackingAction.autoSelect(quest, true, false, QuestTrackingChangeReason.AUTO_ON_ACCEPT);
    }
    private static QuestTrackingAction terminated() {
        return QuestTrackingAction.autoSelect(null, false, true, QuestTrackingChangeReason.AUTO_ON_TERMINATED);
    }
    private static QuestTrackingAction reconcile() {
        return QuestTrackingAction.reconcile(QuestTrackingChangeReason.PLAYER_LOADED);
    }
}
