package org.arcadia.arc_quest.trade.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TradeUpdatesPacketTest {
    @Test
    void snapshotAndAcknowledgementRoundTrip() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var snapshot = new S2CTradeUpdatesPacket("shop", 42, Map.of("item", new S2CTradeUpdatesPacket.Entry("food", 5, 3)));
            S2CTradeUpdatesPacket.encode(snapshot, buffer);
            assertEquals(snapshot, S2CTradeUpdatesPacket.decode(buffer));
            buffer.clear();
            var ack = new C2SReadTradeUpdatePacket("shop", "item", 3, 42);
            C2SReadTradeUpdatePacket.encode(ack, buffer);
            assertEquals(ack, C2SReadTradeUpdatePacket.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test
    void refusesUnboundedEntryCountBeforeAllocation() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeUtf("shop");
            buffer.writeLong(42);
            buffer.writeVarInt(Integer.MAX_VALUE);
            assertThrows(IllegalArgumentException.class, () -> S2CTradeUpdatesPacket.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test
    void rejectsUnknownReasons() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            S2CTradeUpdatesPacket.encode(new S2CTradeUpdatesPacket("shop", 42,
                    Map.of("item", new S2CTradeUpdatesPacket.Entry("food", 64, 3))), buffer);
            assertThrows(IllegalArgumentException.class, () -> S2CTradeUpdatesPacket.decode(buffer));
        } finally { buffer.release(); }
    }
}
