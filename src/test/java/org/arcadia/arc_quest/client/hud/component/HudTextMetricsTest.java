package org.arcadia.arc_quest.client.hud.component;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class HudTextMetricsTest {
    @Test void cjkSamplesSurviveSmallJournalTextAtEverySupportedScale() {
        for (double pixelsPerUnit : new double[]{1.5, 2, 2.5, 3, 3.75}) {
            for (float requested : new float[]{.65f, .72f, .84f, .92f, 1, 1.2f}) {
                float actual = HudTextMetrics.readableScale(requested, pixelsPerUnit);
                assertTrue(actual * pixelsPerUnit >= 2 - 1e-6);
                assertTrue(actual >= requested);
            }
        }
        assertEquals(.92f, HudTextMetrics.readableScale(.92f, 2.5));
    }

    @Test void oddAndEvenLabelsUseTheActualInkHeightAndFractionalCenters() {
        for (int boxWidth : new int[]{41, 42, 101}) for (float textWidth : new float[]{17, 18, 63}) {
            var position = HudTextMetrics.centered(7, 11, boxWidth, 19, textWidth, 8, .9f);
            assertEquals(7 + boxWidth / 2f, position.x() + textWidth * .9f / 2, 1e-5);
            assertEquals(20.5f, position.y() + 8 * .9f / 2, 1e-5);
        }
    }

    @Test void physicalPixelSnapIncludesAnimatedTranslationAndMovesLessThanHalfAPixel() {
        for (double pixelsPerUnit : new double[]{1.5, 2, 2.5, 3}) for (double translation : new double[]{0, .2, 13.7, -2.25}) {
            float snapped = HudTextMetrics.snap(7.25f, pixelsPerUnit, translation);
            double physical = snapped * pixelsPerUnit + translation;
            assertEquals(Math.rint(physical), physical, 1e-5);
            assertTrue(Math.abs(snapped - 7.25f) * pixelsPerUnit <= .50001);
        }
    }

    @Test void invalidScaleCannotPoisonTextLayout() {
        assertThrows(IllegalArgumentException.class, () -> HudTextMetrics.readableScale(.9f, 0));
        assertThrows(IllegalArgumentException.class, () -> HudTextMetrics.readableScale(Float.NaN, 2.5));
        assertThrows(IllegalArgumentException.class, () -> HudTextMetrics.readableScale(.9f, Double.POSITIVE_INFINITY));
    }
}
