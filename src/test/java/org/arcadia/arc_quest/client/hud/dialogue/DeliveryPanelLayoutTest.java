package org.arcadia.arc_quest.client.hud.dialogue;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryPanelLayoutTest {
    @Test void emptyFeedbackCollapsesAndNavigationOnlyOccupiesSpaceWhenNeeded() {
        var plain = DeliveryPanelLayout.measure(480, 6, 0);
        var warning = DeliveryPanelLayout.measure(480, 6, 2);
        assertEquals(0, plain.navigationHeight());
        assertEquals(0, plain.feedbackHeight());
        assertEquals(24, warning.height() - plain.height());
        assertEquals(24 + plain.rows() * 24 + 2, plain.toolbarY());
        var scrolling = DeliveryPanelLayout.measure(270, 12, 0);
        assertEquals(30, scrolling.navigationHeight());
        assertEquals(24 + scrolling.rows() * 24 + 32, scrolling.toolbarY());
    }
    @Test void sixAndSevenIngredientRecipesGrowBeyondThreeRows() {
        assertEquals(6, DeliveryPanelLayout.measure(480, 6).rows());
        assertEquals(7, DeliveryPanelLayout.measure(480, 7).rows());
        assertTrue(DeliveryPanelLayout.measure(480, 7).height() > DeliveryPanelLayout.measure(480, 6).height());
    }
    @Test void catalogueFitsScreenBetweenCinematicBarsAndCapsAtEightRows() {
        for (int height : new int[]{240, 270, 360, 480, 720, 1080}) {
            var layout = DeliveryPanelLayout.measure(height, 128);
            assertTrue(layout.rows() >= 1 && layout.rows() <= 8);
            assertTrue(layout.height() <= height - 2 * Math.max(24, (int)(height * .08f)) - 12);
            assertTrue(layout.height() <= 420);
        }
        assertEquals(8, DeliveryPanelLayout.measure(1080, 128).rows());
    }
    @Test void draggingThumbReachesBothEndsAndShortCataloguesDoNotScroll() {
        var layout = DeliveryPanelLayout.measure(360, 12);
        assertEquals(0, layout.offsetForThumb(-1000, 24, layout.rows() * 24 - 2, 12));
        assertEquals(layout.maxOffset(12), layout.offsetForThumb(1000, 24, layout.rows() * 24 - 2, 12));
        assertEquals(0, DeliveryPanelLayout.measure(480, 4).maxOffset(4));
        assertEquals(1, DeliveryPanelLayout.measure(480, 0).rows());
    }
}
