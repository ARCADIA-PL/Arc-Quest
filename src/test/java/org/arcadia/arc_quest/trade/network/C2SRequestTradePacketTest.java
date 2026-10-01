package org.arcadia.arc_quest.trade.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class C2SRequestTradePacketTest {
    @Test
    void purchaseRoundTripPreservesFieldOrderRequestIdAndEpoch() {
        UUID id = UUID.randomUUID();
        var original = C2SRequestTradePacket.purchaseWithScreenType("shop", "entry",
                C2SRequestTradePacket.ScreenType.SIMPLE, id, 123L);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            original.encode(buffer);
            var decoded = C2SRequestTradePacket.decode(buffer);
            assertEquals(C2SRequestTradePacket.Action.PURCHASE, decoded.getAction());
            assertEquals("shop", decoded.getShopId());
            assertEquals("entry", decoded.getEntryId());
            assertEquals(C2SRequestTradePacket.ScreenType.SIMPLE, decoded.getCurrentScreenType());
            assertEquals(id, decoded.getRequestId());
            assertEquals(123L, decoded.getPlayerSessionEpoch());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void legacyConstructorAndPreviouslyValidLongIdentifiersRemainAccepted() {
        String longId = "shop".repeat(100);
        var original = new C2SRequestTradePacket(C2SRequestTradePacket.Action.OPEN_FULL, longId, null);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            original.encode(buffer);
            var decoded = C2SRequestTradePacket.decode(buffer);
            assertEquals(longId, decoded.getShopId());
            assertEquals("", decoded.getEntryId());
            assertEquals(0L, decoded.getPlayerSessionEpoch());
            assertEquals(original.getRequestId(), decoded.getRequestId());
        } finally {
            buffer.release();
        }
    }
}
