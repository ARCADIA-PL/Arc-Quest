package org.arcadia.arc_quest.quest.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class C2SSetTrackedQuestPacketTest {

    @Test
    void preservesTrackedPhaseFocusDuringRoundTrip() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            C2SSetTrackedQuestPacket.encode(
                    new C2SSetTrackedQuestPacket("arc_quest:epic_prologue", "arc_quest:scout_forest"),
                    buffer);

            C2SSetTrackedQuestPacket decoded = C2SSetTrackedQuestPacket.decode(buffer);

            assertEquals("arc_quest:epic_prologue", decoded.questId());
            assertEquals("arc_quest:scout_forest", decoded.phaseId());
        } finally {
            buffer.release();
        }
    }
}
