package org.arcadia.arc_quest.client.tutorial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.arcadia.arc_quest.client.tutorial.TutorialTooltipPlacement.*;

class TutorialTooltipPlacementTest {
    @Test
    void fixedSalvageResultsMoveAwayFromTheLeftHandLesson() {
        Box card = new Box(24, 21, 171, 69);
        var positioner = nativePositioner(406, 228, 42, 30);
        assertTrue(positioner.position(155, 35).overlaps(card));
        var anchor = avoid(155, 35, 406, 228, card, positioner);
        assertReadable(anchor, positioner, card, 406, 228);
        assertEquals(anchor, avoid(155, 35, 406, 228, card, positioner));
    }

    @Test
    void unrelatedTooltipsKeepTheirExistingAnchor() {
        var positioner = nativePositioner(406, 228, 42, 30);
        assertEquals(new Anchor(240, 140), avoid(240, 140, 406, 228,
                new Box(24, 21, 171, 69), positioner));
    }

    @Test
    void hoverTooltipsRemainReadableAtBothScreenEdges() {
        for (Box card : new Box[]{new Box(6, 6, 132, 72), new Box(266, 150, 132, 72)}) {
            var positioner = nativePositioner(406, 228, 100, 48);
            int x = card.x() + 8, y = card.y() + 30;
            assertTrue(positioner.position(x, y).overlaps(card));
            assertReadable(avoid(x, y, 406, 228, card, positioner), positioner, card, 406, 228);
        }
    }

    @Test
    void honorsTheSuppliedPositionerInsteadOfAssumingVanillaOffsets() {
        Box card = new Box(20, 20, 150, 90);
        Positioner decorated = (x, y) -> new Box(Math.max(6, Math.min(306, x + 27)),
                Math.max(6, Math.min(182, y + 9)), 94, 40);
        assertReadable(avoid(20, 20, 406, 228, card, decorated), decorated, card, 406, 228);
    }

    @Test
    void impossibleLayoutDoesNotDisplaceOrHideTheLesson() {
        Box card = new Box(6, 6, 188, 88);
        assertNull(avoid(20, 20, 200, 100, card, nativePositioner(200, 100, 90, 50)));
    }

    private static Positioner nativePositioner(int screenWidth, int screenHeight, int width, int height) {
        return (x, y) -> {
            int left = x + 12, top = y - 12;
            if (left + width > screenWidth) left = Math.max(4, x - 16 - width);
            if (top + height + 6 > screenHeight) top = screenHeight - height - 6;
            return new Box(left - 6, top - 6, width + 12, height + 12);
        };
    }

    private static void assertReadable(Anchor anchor, Positioner positioner, Box card, int width, int height) {
        assertNotNull(anchor);
        var tooltip = positioner.position(anchor.x(), anchor.y());
        assertFalse(tooltip.overlaps(card), tooltip + " overlaps " + card);
        assertTrue(tooltip.x() >= 6 && tooltip.y() >= 6
                && tooltip.x() + tooltip.w() <= width - 6 && tooltip.y() + tooltip.h() <= height - 6);
    }
}
