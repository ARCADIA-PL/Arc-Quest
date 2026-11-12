package org.arcadia.arc_quest.client.hud.dialogue;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DialogueChoiceLayoutTest {
    @Test void allNineChoicesRemainVisibleAtSupportedWindowAndTextScales() {
        for (int viewport : new int[]{180, 216, 240, 270, 300, 360, 540, 720}) {
            for (int count = 1; count <= 9; count++) {
                var layout = DialogueChoiceLayout.fit(viewport, count, 9);
                assertTrue(layout.startY() >= 8);
                assertTrue(layout.height() >= 13);
                assertTrue(layout.startY() + count * layout.height() + (count - 1) * layout.gap() <= viewport - 8,
                        "Clipped choice at viewport=" + viewport + " count=" + count);
            }
        }
    }
    @Test void sparseMenusRetainTheExistingButtonHeightAndGap() {
        var layout = DialogueChoiceLayout.fit(270, 3, 9);
        assertEquals(34, layout.height());
        assertEquals(8, layout.gap());
        assertEquals((270 - (3 * 42 - 8)) / 2 + 27, layout.startY());
    }
    @Test void eightChoiceRegressionFitsTheActualClientCoordinateHeight() {
        var layout = DialogueChoiceLayout.fit(270, 8, 9);
        assertEquals(24, layout.height());
        assertEquals(4, layout.gap());
        assertTrue(layout.startY() + 8 * layout.height() + 7 * layout.gap() <= 248);
    }
}
