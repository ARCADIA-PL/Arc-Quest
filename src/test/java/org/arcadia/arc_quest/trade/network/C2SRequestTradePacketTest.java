package org.arcadia.arc_quest.trade.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class C2SRequestTradePacketTest {

    @Test
    void roundTripsIdempotencyAndLoginContext() {
        UUID requestId = UUID.randomUUID();
        C2SRequestTradePacket packet = new C2SRequestTradePacket(
                C2SRequestTradePacket.Action.PURCHASE,
                "arc_quest:test_shop",
                "entry_one",
                C2SRequestTradePacket.ScreenType.SIMPLE,
                requestId,
                42L);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        packet.encode(buffer);
        C2SRequestTradePacket decoded = C2SRequestTradePacket.decode(buffer);

        assertEquals(C2SRequestTradePacket.Action.PURCHASE, decoded.getAction());
        assertEquals("arc_quest:test_shop", decoded.getShopId());
        assertEquals("entry_one", decoded.getEntryId());
        assertEquals(C2SRequestTradePacket.ScreenType.SIMPLE, decoded.getCurrentScreenType());
        assertEquals(requestId, decoded.getRequestId());
        assertEquals(42L, decoded.getPlayerSessionEpoch());
    }
}
