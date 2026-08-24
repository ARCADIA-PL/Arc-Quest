package org.arcadia.arc_quest.client.hud.quest.trackingmenu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestTrackingMenuCardRendererTest {
    @Test
    void usesGuiScaleThreeAsReferenceLayout() {
        assertEquals(1.5f, QuestTrackingMenuCardRenderer.guiScaleCorrection(2.0), 0.001f);
        assertEquals(1.0f, QuestTrackingMenuCardRenderer.guiScaleCorrection(3.0), 0.001f);
        assertEquals(0.75f, QuestTrackingMenuCardRenderer.guiScaleCorrection(4.0), 0.001f);
    }
}
