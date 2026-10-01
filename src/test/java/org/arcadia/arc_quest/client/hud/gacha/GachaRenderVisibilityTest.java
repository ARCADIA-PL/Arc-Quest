package org.arcadia.arc_quest.client.hud.gacha;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GachaRenderVisibilityTest {
    @Test void largePreviewPoolsCompleteWithoutChangingTheFirstRowsStagger() {
        for (int index : new int[] {0, 10, 29, 30, 299, 599})
            assertEquals(1f, GachaRenderVisibility.previewProgress(1, index));
        assertEquals(0f, GachaRenderVisibility.previewProgress(0, 599));
        assertEquals(.7f * 1.5f - 9 * .05f, GachaRenderVisibility.previewProgress(.7f, 9));
    }
    @Test void includesPartiallyVisibleCardsAndScaledEdges() {
        assertTrue(GachaRenderVisibility.cardIntersectsScreen(-70, 70, 1.07f, 960));
        assertTrue(GachaRenderVisibility.cardIntersectsScreen(965, 70, 1.07f, 960));
        assertTrue(GachaRenderVisibility.cardIntersectsScreen(450, 70, .2f, 960));
    }
    @Test void skipsBothOffscreenExitDirectionsAfterMovement() {
        assertFalse(GachaRenderVisibility.cardIntersectsScreen(100 - 600, 70, 1, 960));
        assertFalse(GachaRenderVisibility.cardIntersectsScreen(900 + 600, 70, 1, 960));
        assertFalse(GachaRenderVisibility.cardIntersectsScreen(-200, 70, 1, 960));
    }
}
