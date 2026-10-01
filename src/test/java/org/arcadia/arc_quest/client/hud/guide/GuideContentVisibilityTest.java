package org.arcadia.arc_quest.client.hud.guide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuideContentVisibilityTest {
    @Test void partiallyVisibleIntroIconsAndLinesRemainSubmitted() {
        assertTrue(GuideContentVisibility.intersects(0, 32, 30, 200));
        assertTrue(GuideContentVisibility.intersects(187.5, 8.55, 0, 200));
        assertTrue(GuideContentVisibility.intersects(0, 8.55, 21.5, 200));
        assertFalse(GuideContentVisibility.intersects(0, 32, 60, 200));
        assertFalse(GuideContentVisibility.intersects(205, 8.55, 0, 200));
    }

    @Test void longScrolledGuidesOnlySubmitTheVisibleLineWindow() {
        int submitted = 0;
        for (int line = 0; line < 1000; line++) {
            if (GuideContentVisibility.intersects(line * 14, 9, 5000.25, 140)) submitted++;
        }
        assertTrue(submitted >= 10 && submitted <= 12, "Only the viewport and conservative edge margin should draw");
        assertTrue(GuideContentVisibility.intersects(5000.25, 9, 5000.25, 140));
    }

    @Test void invalidAnimationCoordinatesFallBackToDrawing() {
        assertTrue(GuideContentVisibility.intersects(Double.NaN, 9, 0, 100));
        assertTrue(GuideContentVisibility.intersects(0, 9, Double.POSITIVE_INFINITY, 100));
    }
}
