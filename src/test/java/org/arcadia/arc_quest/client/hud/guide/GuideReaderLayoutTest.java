package org.arcadia.arc_quest.client.hud.guide;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GuideReaderLayoutTest {
    @Test void supportedViewportsKeepReadingAndControlsInsideThePanel() {
        for (int[] size : new int[][]{{320, 240}, {427, 240}, {480, 270}, {640, 360},
                {854, 480}, {960, 540}, {1280, 720}, {1920, 1080}, {2560, 720}}) {
            for (float scale : new float[]{.5f, 1, 1.5f, 2}) {
                for (int titleLines : new int[]{1, 2, 8}) {
                    for (boolean media : new boolean[]{false, true}) {
                        var l = GuideReaderLayout.measure(size[0], size[1], titleLines, 9, media, false, scale);
                        assertInside(new GuideReaderLayout.Box(0, 0, size[0], size[1]), l.panel());
                        assertEquals(0, l.panel().x());
                        assertTrue(l.panel().width() <= Math.ceil(size[0] * .62));
                        assertFalse(l.columns());
                        for (var box : new GuideReaderLayout.Box[]{l.title(), l.body(), l.close(),
                                l.previous(), l.next(), l.settings(), l.media()}) assertInside(l.panel(), box);
                        assertTrue(l.body().height() >= Math.ceil(9 * scale) * 2, l.toString());
                        assertTrue(l.body().bottom() < l.footerY());
                        assertTrue(l.media().height() == 0 || l.columns()
                                || l.media().bottom() < l.body().y());
                        assertFalse(l.previous().contains(l.next().x(), l.next().y()));
                    }
                }
            }
        }
    }

    @Test void mediaNeverExpandsTheSidebarOrReservesSpaceOnTextOnlyPages() {
        var text = GuideReaderLayout.measure(960, 540, 1, 9, false, false, 1);
        assertEquals(0, text.media().height());
        var regular = GuideReaderLayout.measure(960, 540, 1, 9, true, false, 1);
        var enlarged = GuideReaderLayout.measure(960, 540, 1, 9, true, false, 2);
        assertFalse(regular.columns());
        assertFalse(enlarged.columns());
        assertEquals(regular.body().width(), enlarged.body().width());
    }

    @Test void scaledLineAndThumbMeasurementsNeverOverlapOrExceedTheTrack() {
        for (float scale : new float[]{.5f, .9f, 1, 1.25f, 1.5f, 2})
            assertTrue(GuideReaderLayout.lineAdvance(9, scale) > Math.ceil(9 * scale));
        for (int viewport : new int[]{1, 8, 16, 80, 400})
            for (int content : new int[]{0, 1, 10, 100, 10000}) {
                int thumb = GuideReaderLayout.thumbHeight(viewport, content);
                assertTrue(thumb > 0 && thumb <= viewport);
            }
    }

    private static void assertInside(GuideReaderLayout.Box outer, GuideReaderLayout.Box inner) {
        assertTrue(inner.x() >= outer.x() && inner.y() >= outer.y()
                && inner.right() <= outer.right() && inner.bottom() <= outer.bottom(),
                () -> inner + " outside " + outer);
    }
}
