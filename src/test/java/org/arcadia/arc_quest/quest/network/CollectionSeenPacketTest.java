package org.arcadia.arc_quest.quest.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CollectionSeenPacketTest {
    @Test void categoryAcknowledgementsRoundTripAsOneBoundedBatch() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var packet = new C2SMarkCollectionSeenPacket("test:catalogue", "weapons", List.of("longsword", "katana"));
            C2SMarkCollectionSeenPacket.encode(packet, buffer);
            assertEquals(packet, C2SMarkCollectionSeenPacket.decode(buffer));
            assertEquals(List.of("longsword"), new C2SMarkCollectionSeenPacket("test:catalogue", "weapons", "longsword").bindingIds());
        } finally { buffer.release(); }
    }

    @Test void oversizedNetworkAcknowledgementsAreRejectedBeforeAllocation() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeUtf("test:catalogue"); buffer.writeUtf("weapons"); buffer.writeVarInt(257);
            assertThrows(RuntimeException.class, () -> C2SMarkCollectionSeenPacket.decode(buffer));
        } finally { buffer.release(); }
    }
}
