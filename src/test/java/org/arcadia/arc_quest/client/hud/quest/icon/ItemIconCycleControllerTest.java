package org.arcadia.arc_quest.client.hud.quest.icon;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ItemIconCycleControllerTest {
    private static final List<String> ITEMS = List.of("oak", "birch", "spruce");
    @Test void frameRateDoesNotChangeSelection() {
        for (int fps : List.of(30, 60, 144)) {
            var cycle = new ItemIconCycleController();
            cycle.select(ITEMS, 0, false);
            for (int frame = 1; frame <= fps * 2; frame++) cycle.select(ITEMS, frame * 1000L / fps, false);
            assertEquals(2, cycle.select(ITEMS, 2000, false));
        }
    }
    @Test void hoverFreezesBeforeTheBoundaryAndResumesRemainingTime() {
        var cycle = new ItemIconCycleController();
        cycle.select(ITEMS, 0, false);
        cycle.select(ITEMS, 900, false);
        assertEquals(0, cycle.select(ITEMS, 1001, true));
        assertEquals(0, cycle.select(ITEMS, 9000, true));
        assertEquals(0, cycle.select(ITEMS, 9010, false));
        assertEquals(1, cycle.select(ITEMS, 9110, false));
    }
    @Test void tagReloadPreservesSelectedIdentityAndRemovesInvalidItems() {
        var cycle = new ItemIconCycleController();
        cycle.select(ITEMS, 0, false);
        assertEquals(1, cycle.select(ITEMS, 1000, false));
        assertEquals(0, cycle.select(List.of("birch", "oak"), 1001, false));
        assertEquals(0, cycle.select(List.of("spruce", "oak"), 1002, false));
        assertEquals(-1, cycle.select(List.of(), 1003, false));
    }
    @Test void jeiOrHiddenRowSuspensionDoesNotCatchUpElapsedTime() {
        var cycle = new ItemIconCycleController();
        cycle.select(ITEMS, 0, false);
        cycle.select(ITEMS, 1500, false);
        cycle.suspend();
        assertEquals(1, cycle.select(ITEMS, 900000, false));
        assertEquals(2, cycle.select(ITEMS, 900500, false));
    }
    @Test void singleItemNeverChangesAndClockRegressionIsSafe() {
        var cycle = new ItemIconCycleController();
        assertEquals(0, cycle.select(List.of("oak"), 1000, false));
        assertEquals(0, cycle.select(List.of("oak"), 10, false));
        assertEquals(0, cycle.select(List.of("oak"), Long.MAX_VALUE / 2, false));
        assertThrows(IllegalArgumentException.class, () -> new ItemIconCycleController(0));
    }
}
