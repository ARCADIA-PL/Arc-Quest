package org.arcadia.arc_quest.api.event.guide;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuideCompletedEventTest {

    @Test
    void eventExposesCompletedGuideId() {
        ResourceLocation guideId = ResourceLocation.parse("arc_quest:diamond_demo");

        GuideCompletedEvent event = new GuideCompletedEvent(null, guideId);

        assertEquals(guideId, event.getGuideId());
    }
}
