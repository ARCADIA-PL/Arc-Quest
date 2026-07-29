package org.arcadia.arc_quest.guide.network;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideOpenRequestQueueTest {

    @AfterEach
    void clearCache() {
        ClientGuideCache.INSTANCE.clear();
    }

    @Test
    void preservesMultipleOpenRequestsInOrder() {
        ResourceLocation first = ResourceLocation.parse("test:first");
        ResourceLocation second = ResourceLocation.parse("test:second");

        ClientGuideCache.INSTANCE.requestOpen(first, 0, true);
        ClientGuideCache.INSTANCE.requestOpen(second, 2, false);

        assertEquals(first, ClientGuideCache.INSTANCE.consumePendingOpenRequest()
                .orElseThrow().guideId());
        assertEquals(second, ClientGuideCache.INSTANCE.consumePendingOpenRequest()
                .orElseThrow().guideId());
        assertTrue(ClientGuideCache.INSTANCE.consumePendingOpenRequest().isEmpty());
    }

    @Test
    void ignoresIdenticalPendingRequests() {
        ResourceLocation guide = ResourceLocation.parse("test:guide");

        ClientGuideCache.INSTANCE.requestOpen(guide, 0, true);
        ClientGuideCache.INSTANCE.requestOpen(guide, 0, true);

        assertEquals(guide, ClientGuideCache.INSTANCE.consumePendingOpenRequest()
                .orElseThrow().guideId());
        assertTrue(ClientGuideCache.INSTANCE.consumePendingOpenRequest().isEmpty());
    }

    @Test
    void clearRemovesPendingRequestsAndGuideProgress() {
        ResourceLocation guide = ResourceLocation.parse("test:guide");
        ClientGuideCache.INSTANCE.requestOpen(guide, 0, true);
        ClientGuideCache.INSTANCE.applyLocalProgress(guide, 3);

        ClientGuideCache.INSTANCE.clear();

        assertTrue(ClientGuideCache.INSTANCE.consumePendingOpenRequest().isEmpty());
        assertEquals(0, ClientGuideCache.INSTANCE.getProgress(guide));
    }
}
