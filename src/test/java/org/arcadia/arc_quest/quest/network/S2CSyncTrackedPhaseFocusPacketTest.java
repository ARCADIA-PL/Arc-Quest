package org.arcadia.arc_quest.quest.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class S2CSyncTrackedPhaseFocusPacketTest {

    @Test
    void preservesTrackedPhaseFocusDuringRoundTrip() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            S2CSyncTrackedPhaseFocusPacket.encode(
                    new S2CSyncTrackedPhaseFocusPacket(
                            "arc_quest:epic_prologue", "arc_quest:scout_forest", 7L, 10L, 11L),
                    buffer);

            S2CSyncTrackedPhaseFocusPacket decoded =
                    S2CSyncTrackedPhaseFocusPacket.decode(buffer);

            assertEquals("arc_quest:epic_prologue", decoded.questId());
            assertEquals("arc_quest:scout_forest", decoded.phaseId());
            assertEquals(7L, decoded.playerSessionEpoch());
            assertEquals(10L, decoded.baseRevision());
            assertEquals(11L, decoded.newRevision());
        } finally {
            buffer.release();
        }
    }
}
