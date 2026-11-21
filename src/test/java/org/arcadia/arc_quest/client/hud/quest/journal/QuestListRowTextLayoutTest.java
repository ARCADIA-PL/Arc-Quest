package org.arcadia.arc_quest.client.hud.quest.journal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuestListRowTextLayoutTest {
    @Test void titlesShareOneScaleAcrossPanelInsetsAndWidths() {
        for (int lineHeight : new int[]{7, 9, 12, 18}) {
            float titleScale = QuestListRowTextLayout.titleScale(lineHeight);
            assertEquals(titleScale * .85f, QuestListRowTextLayout.groupSubtitleScale(lineHeight));
            for (int width : new int[]{1, 24, 92, 220}) {
                for (boolean collection : new boolean[]{false, true}) {
                    var standard = QuestListRowTextLayout.measure(0, 24, lineHeight, width, collection);
                    var ore = QuestListRowTextLayout.measure(2, 18, lineHeight, width, collection);
                    assertEquals(titleScale, standard.titleScale());
                    assertEquals(standard.titleScale(), ore.titleScale());
                }
            }
        }
    }

    @Test void collectionProgressFitsAndCentersWithTheTitle() {
        for (int lineHeight : new int[]{7, 9, 12}) {
            for (float top : new float[]{0, 2}) {
                float bottom = top == 0 ? 24 : 17;
                var layout = QuestListRowTextLayout.measure(top, bottom, lineHeight, 92, true);
                assertTrue(layout.progress());
                float titleBottom = layout.titleY() + lineHeight * layout.titleScale();
                assertEquals(titleBottom + 1, layout.progressY(), .0001f);
                float blockBottom = layout.progressY() + lineHeight * layout.progressScale();
                assertEquals(layout.titleY() - top, bottom - blockBottom, .0001f);
            }
        }
    }

    @Test void collapsedRowsKeepRenderedTextInsideTheFace() {
        for (float height : new float[]{0, .1f, .5f, 1, 2, 3, 6, 12, 18, 24, 40}) {
            for (int lineHeight : new int[]{7, 9, 12, 18}) {
                for (int width : new int[]{1, 8, 24, 92, 220}) {
                    var layout = QuestListRowTextLayout.measure(2, 2 + height, lineHeight, width, true);
                    assertTrue(layout.titleY() >= 2);
                    assertTrue(layout.titleY() + lineHeight * layout.titleScale() <= 2 + height + .0001f);
                    assertTrue(layout.titleWidth() * layout.titleScale() <= width + .0001f);
                    if (layout.progress()) {
                        assertTrue(layout.progressY() + lineHeight * layout.progressScale() <= 2 + height + .0001f);
                        assertTrue(layout.progressWidth() * layout.progressScale() <= width + .0001f);
                    }
                }
            }
        }
        assertFalse(QuestListRowTextLayout.measure(2, 8, 9, 92, true).progress());
    }
}
