package org.arcadia.arc_quest.dialogue.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class C2SDialogueChoicePacketTest {

    @Test
    void roundTripsSessionAndIdempotencyContext() {
        UUID sessionId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        C2SDialogueChoicePacket packet = new C2SDialogueChoicePacket(
                2, sessionId, 17L, "node/start", "choice/accept", 91L, requestId);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        packet.encode(buffer);
        C2SDialogueChoicePacket decoded = C2SDialogueChoicePacket.decode(buffer);

        assertEquals(2, decoded.getChoiceIndex());
        assertEquals(sessionId, decoded.getSessionId());
        assertEquals(17L, decoded.getExpectedRevision());
        assertEquals("node/start", decoded.getExpectedNodeId());
        assertEquals("choice/accept", decoded.getExpectedChoiceId());
        assertEquals(91L, decoded.getPlayerSessionEpoch());
        assertEquals(requestId, decoded.getRequestId());
    }
}
