package org.arcadia.arc_quest.client.hud.quest.tracker;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrackerStyleModelTest {
    @Test
    void countsObjectivesEquallyInsteadOfAddingDifferentItemTotals() {
        var summary = TrackerStyleModel.summarize(List.of(
                objective(0, 1, 1), objective(1, 0, 1000)));
        assertEquals(1, summary.completed());
        assertEquals(2, summary.total());
        assertEquals(1, summary.remaining());
        assertEquals(0.5, summary.progress(), 0.0001);
    }

    @Test
    void dynamicRequirementsAndBooleanRowsProduceCorrectNormalizedRatios() {
        var summary = TrackerStyleModel.summarize(List.of(objective(0, 3, 6),
                new TrackerStyleModel.Objective(1, 1, 2, false, true, false)));
        assertEquals(0.25, summary.progress(), 0.0001);
        assertEquals(0, summary.completed());
        assertEquals(0, new TrackerStyleModel.Objective(1, 1, 2, false, true, false).ratio());
        assertEquals(1, new TrackerStyleModel.Objective(1, 2, 2, false, true, false).ratio());
    }

    @Test
    void focusKeepsOriginalIndicesAndRefillsAfterACompletion() {
        var first = List.of(objective(0, 1, 1), objective(1, 0, 8), objective(2, 0, 3),
                objective(3, 0, 2), objective(4, 0, 10));
        assertEquals(List.of(1, 2, 3), TrackerStyleModel.unfinished(first, 3).stream()
                .map(TrackerStyleModel.Objective::index).toList());
        var second = List.of(objective(0, 1, 1), objective(1, 8, 8), objective(2, 0, 3),
                objective(3, 0, 2), objective(4, 0, 10));
        assertEquals(List.of(2, 3, 4), TrackerStyleModel.unfinished(second, 3).stream()
                .map(TrackerStyleModel.Objective::index).toList());
    }

    @Test
    void hiddenObjectivesNeverLeakAndInstructionRowsAreNotProgress() {
        var hidden = new TrackerStyleModel.Objective(0, 0, 100, true, true, true);
        var instruction = new TrackerStyleModel.Objective(1, 0, 1, false, false, false);
        var inputs = List.of(hidden, instruction, objective(2, 1, 2));
        assertEquals(new TrackerStyleModel.Summary(0, 1, 0.5), TrackerStyleModel.summarize(inputs));
        assertEquals(List.of(1, 2), TrackerStyleModel.unfinished(inputs, 3).stream()
                .map(TrackerStyleModel.Objective::index).toList());
        assertEquals(2, TrackerStyleModel.unfinishedCount(inputs));
    }

    @Test
    void emptyStagesDoNotPretendToBeCompleted() {
        assertEquals(new TrackerStyleModel.Summary(0, 0, 0), TrackerStyleModel.summarize(List.of()));
        assertTrue(TrackerStyleModel.unfinished(List.of(), 3).isEmpty());
    }

    @Test
    void progressClampsAtBothEndsWithoutOverflow() {
        assertEquals(0, objective(0, -20, 4).ratio());
        assertEquals(1, objective(0, Integer.MAX_VALUE, 1).ratio());
        assertEquals(1, objective(0, 2, 0).required());
    }

    @Test
    void overviewAlwaysIncludesFocusAndPreservesStageOrder() {
        assertEquals(List.of(0, 1, 2, 3), TrackerStyleModel.phaseWindow(8, 0, 4));
        assertEquals(List.of(3, 4, 5, 6), TrackerStyleModel.phaseWindow(8, 6, 4));
        assertEquals(List.of(4, 5, 6, 7), TrackerStyleModel.phaseWindow(8, 7, 4));
        assertEquals(List.of(0), TrackerStyleModel.phaseWindow(1, 0, 4));
        assertTrue(TrackerStyleModel.phaseWindow(8, 5, 0).isEmpty());
    }

    @Test
    void zeroDetailLimitIsRespected() {
        assertTrue(TrackerStyleModel.unfinished(List.of(objective(0, 0, 1)), 0).isEmpty());
    }

    private static TrackerStyleModel.Objective objective(int index, int progress, int required) {
        return new TrackerStyleModel.Objective(index, progress, required, true, true, false);
    }
}
