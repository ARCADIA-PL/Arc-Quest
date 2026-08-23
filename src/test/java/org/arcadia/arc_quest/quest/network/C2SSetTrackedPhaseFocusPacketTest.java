package org.arcadia.arc_quest.quest.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class C2SSetTrackedPhaseFocusPacketTest {

    @Test
    void preservesTrackedPhaseFocusDuringRoundTrip() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            C2SSetTrackedPhaseFocusPacket.encode(
                    new C2SSetTrackedPhaseFocusPacket(
                            "arc_quest:epic_prologue", "arc_quest:scout_forest"),
                    buffer);

            C2SSetTrackedPhaseFocusPacket decoded = C2SSetTrackedPhaseFocusPacket.decode(buffer);

            assertEquals("arc_quest:epic_prologue", decoded.questId());
            assertEquals("arc_quest:scout_forest", decoded.phaseId());
        } finally {
            buffer.release();
        }
    }
}
