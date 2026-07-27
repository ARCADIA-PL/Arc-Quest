package org.arcadia.arc_quest.client.hud.guide;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuidePopupLayoutTest {

    @Test
    void popupUsesTheSameCompactWidthAsTheStandaloneGuide() {
        assertEquals(224, GuideConstants.guidePanelWidth(800));
        assertEquals(270, GuideConstants.guidePanelWidth(1_000));
    }

    @Test
    void popupHeightShrinksForShortContent() {
        assertEquals(159, GuideConstants.guidePopupHeight(600, 159));
        assertEquals(112, GuideConstants.guidePopupHeight(600, 80));
    }

    @Test
    void popupHeightIsLimitedByTheScreenAndScrollbarRange() {
        assertEquals(360, GuideConstants.guidePopupHeight(600, 500));
        assertEquals(168, GuideConstants.guidePopupHeight(200, 500));
    }
}
