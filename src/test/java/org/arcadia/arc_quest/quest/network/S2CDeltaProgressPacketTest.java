package org.arcadia.arc_quest.quest.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class S2CDeltaProgressPacketTest {
    @Test void roundTripRetainsStableObjectiveIdAndEffectiveLargeCount() {
        var original = new S2CDeltaProgressPacket("arc_quest:test", "parallel", "stable_id",
                3, 65537, Integer.MAX_VALUE, 21L, 55L, 56L);
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            S2CDeltaProgressPacket.encode(original, buf);
            var decoded = S2CDeltaProgressPacket.decode(buf);
            assertEquals(0, buf.readableBytes());
            assertEquals(original.getQuestId(), decoded.getQuestId());
            assertEquals("parallel", decoded.getPhaseId());
            assertEquals("stable_id", decoded.getObjectiveId());
            assertEquals(3, decoded.getObjectiveIndex());
            assertEquals(65537, decoded.getNewProgress());
            assertEquals(Integer.MAX_VALUE, decoded.getRequiredCount());
            assertEquals(21L, decoded.getPlayerSessionEpoch());
            assertEquals(55L, decoded.getBaseRevision());
            assertEquals(56L, decoded.getNewRevision());
        } finally { buf.release(); }
    }

    @Test void existingJavaConstructorsKeepAnUnspecifiedCountForFallback() {
        assertEquals(0, new S2CDeltaProgressPacket("q", "p", 0, 2).getRequiredCount());
        assertEquals(0, new S2CDeltaProgressPacket("q", "p", 0, 2, 1L, 2L, 3L).getRequiredCount());
        assertEquals(0, new S2CDeltaProgressPacket("q", "p", "o", 0, 2, 1L, 2L, 3L).getRequiredCount());
    }
}
