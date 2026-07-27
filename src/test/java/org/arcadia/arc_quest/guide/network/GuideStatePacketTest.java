package org.arcadia.arc_quest.guide.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuideStatePacketTest {

    @Test
    void syncPacketRoundTripsUnlockSeenAndProgress() {
        ResourceLocation guideId = ResourceLocation.parse("arc_quest:diamond_demo");
        S2CSyncGuideStatePacket packet = new S2CSyncGuideStatePacket(
                List.of(guideId), List.of(guideId), Map.of(guideId, 2));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        S2CSyncGuideStatePacket.encode(packet, buffer);
        S2CSyncGuideStatePacket decoded = S2CSyncGuideStatePacket.decode(buffer);

        assertEquals(List.of(guideId), decoded.getUnlockedGuides());
        assertEquals(List.of(guideId), decoded.getSeenGuides());
        assertEquals(Map.of(guideId, 2), decoded.getGuideProgress());
    }

    @Test
    void progressPacketRoundTripsGuideAndPage() {
        ResourceLocation guideId = ResourceLocation.parse("arc_quest:diamond_demo");
        C2SUpdateGuideProgressPacket packet = new C2SUpdateGuideProgressPacket(guideId, 3);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        packet.encode(buffer);
        C2SUpdateGuideProgressPacket decoded = C2SUpdateGuideProgressPacket.decode(buffer);

        assertEquals(guideId, decoded.guideId());
        assertEquals(3, decoded.pageIndex());
    }
}
