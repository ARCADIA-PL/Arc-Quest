package org.arcadia.arc_quest.client.hud.guide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuideImageLayoutTest {
    @Test void containShowsTheWholePictureWithoutDistortion() {
        assertEquals(new GuideImageLayout(10, 45, 200, 100),
                GuideImageLayout.measure(10, 20, 200, 150, 400, 200, GuideImageLayout.Fit.CONTAIN));
    }
    @Test void coverCentersTheCropAndPreservesAspectRatio() {
        assertEquals(new GuideImageLayout(-40, 20, 300, 150),
                GuideImageLayout.measure(10, 20, 200, 150, 400, 200, GuideImageLayout.Fit.COVER));
    }
    @Test void enlargementFitsPortraitImagesAndEmptyAreasSafely() {
        assertEquals(new GuideImageLayout(250, 0, 300, 600),
                GuideImageLayout.measure(0, 0, 800, 600, 200, 400, GuideImageLayout.Fit.CONTAIN));
        assertEquals(0, GuideImageLayout.measure(0, 0, 0, 10, 0, 0, GuideImageLayout.Fit.CONTAIN).width());
    }
}
