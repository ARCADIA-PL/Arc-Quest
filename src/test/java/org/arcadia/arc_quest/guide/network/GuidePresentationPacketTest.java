package org.arcadia.arc_quest.guide.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.api.event.guide.GuidePresentationEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuidePresentationPacketTest {
    @Test
    void lifecyclePacketsPreserveTheGuideTokenAndStage() {
        var guide = ResourceLocation.fromNamespaceAndPath("example", "guide");
        var token = UUID.randomUUID();
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (var stage : GuidePresentationEvent.Stage.values()) {
                var ack = new C2SGuidePresentationPacket(guide, token, stage);
                C2SGuidePresentationPacket.STREAM_CODEC.encode(buffer, ack);
                assertEquals(ack, C2SGuidePresentationPacket.STREAM_CODEC.decode(buffer));
            }
            var cancel = new S2CCancelGuidePresentationPacket(token);
            S2CCancelGuidePresentationPacket.STREAM_CODEC.encode(buffer, cancel);
            assertEquals(cancel, S2CCancelGuidePresentationPacket.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void ordinaryAndCorrelatedOpenRequestsConsumeTheirEntirePayload() {
        var guide = ResourceLocation.fromNamespaceAndPath("example", "guide");
        var token = UUID.randomUUID();
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        var encodedAgain = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (var packet : new S2COpenGuidePacket[] {
                    new S2COpenGuidePacket(guide, 2, false), new S2COpenGuidePacket(guide, 2, true, token) }) {
                S2COpenGuidePacket.encode(packet, buffer);
                var copy = buffer.copy();
                try {
                    S2COpenGuidePacket.encode(S2COpenGuidePacket.decode(buffer), encodedAgain);
                    assertEquals(copy, encodedAgain);
                    assertEquals(0, buffer.readableBytes());
                } finally {
                    copy.release();
                    encodedAgain.clear();
                }
            }
        } finally {
            encodedAgain.release();
            buffer.release();
        }
    }
}
