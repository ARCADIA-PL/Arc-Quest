package org.arcadia.arc_quest.client.hud.quest.journal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestListRowTextLayoutTest {
    @Test void titleAndCollectionProgressFormACenteredBlockInTheActualListRow() {
        for (int naturalTitleWidth : new int[]{24, 200, 1200}) {
            for (float hover : new float[]{0, .5f, 1}) {
                var layout = QuestListRowTextLayout.measure(24, 9, 92, naturalTitleWidth, hover, true);
                assertTrue(layout.progress());
                float titleBottom = layout.titleY() + 9 * layout.titleScale();
                assertEquals(titleBottom + 2, layout.progressY(), .0001f);
                float blockBottom = layout.progressY() + 9 * layout.progressScale();
                assertEquals(layout.titleY(), 24 - blockBottom, .0001f);
                assertTrue(layout.titleWidth() * layout.titleScale() <= 92);
                assertTrue(layout.progressWidth() * layout.progressScale() <= 92);
            }
        }
    }

    @Test void collapsedRowsAndDifferentFontSizesNeverPutTextOutsideTheRow() {
        for (float rowHeight : new float[]{0, .1f, .5f, 1, 2, 3, 6, 12, 18, 24, 40}) {
            for (int lineHeight : new int[]{7, 9, 12, 18}) {
                for (int width : new int[]{1, 8, 24, 92, 220}) {
                    var layout = QuestListRowTextLayout.measure(rowHeight, lineHeight, width, 900, 1, true);
                    assertTrue(layout.titleY() >= 0);
                    assertTrue(layout.titleY() + lineHeight * layout.titleScale() <= rowHeight + .0001f);
                    assertTrue(layout.titleWidth() * layout.titleScale() <= width + .0001f);
                    if (layout.progress()) {
                        assertTrue(layout.progressY() >= layout.titleY() + lineHeight * layout.titleScale() + 2);
                        assertTrue(layout.progressY() + lineHeight * layout.progressScale() <= rowHeight + .0001f);
                        assertTrue(layout.progressWidth() * layout.progressScale() <= width + .0001f);
                    }
                }
            }
        }
    }

    @Test void normalRowsCenterOnlyTheTitleAndTinyCollectionRowsOmitProgress() {
        var normal = QuestListRowTextLayout.measure(24, 9, 120, 60, 0, false);
        assertFalse(normal.progress());
        assertEquals((24 - 9) / 2f, normal.titleY());
        assertFalse(QuestListRowTextLayout.measure(12, 9, 120, 60, 0, true).progress());
        var onePixelWide = QuestListRowTextLayout.measure(24, 9, 1, 1, 1, true);
        assertTrue(onePixelWide.titleWidth() * onePixelWide.titleScale() <= 1);
    }
}
