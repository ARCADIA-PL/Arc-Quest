package org.arcadia.arc_quest.quest.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestSyncPacketTest {

    @Test
    void deltaRoundTripsRevisionEnvelope() {
        S2CDeltaProgressPacket packet = new S2CDeltaProgressPacket(
                "arc_quest:test", "phase_one", 3, 7, 42L, 8L, 9L);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        S2CDeltaProgressPacket.encode(packet, buffer);
        S2CDeltaProgressPacket decoded = S2CDeltaProgressPacket.decode(buffer);

        assertEquals("arc_quest:test", decoded.getQuestId());
        assertEquals("phase_one", decoded.getPhaseId());
        assertEquals(3, decoded.getObjectiveIndex());
        assertEquals(7, decoded.getNewProgress());
        assertEquals(42L, decoded.getPlayerSessionEpoch());
        assertEquals(8L, decoded.getBaseRevision());
        assertEquals(9L, decoded.getNewRevision());
    }

    @Test
    void fullSnapshotRoundTripsRevisionEnvelope() {
        S2CSyncFullDataPacket packet = new S2CSyncFullDataPacket(
                new ArcQuestPlayer(UUID.randomUUID()), 42L, 12L);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        S2CSyncFullDataPacket.encode(packet, buffer);
        S2CSyncFullDataPacket decoded = S2CSyncFullDataPacket.decode(buffer);

        assertEquals(42L, decoded.getPlayerSessionEpoch());
        assertEquals(12L, decoded.getRevision());
    }

    @Test
    void resyncRequestRoundTripsClientCursor() {
        C2SRequestQuestResyncPacket packet = new C2SRequestQuestResyncPacket(42L, 11L);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        C2SRequestQuestResyncPacket.encode(packet, buffer);
        C2SRequestQuestResyncPacket decoded = C2SRequestQuestResyncPacket.decode(buffer);

        assertEquals(42L, decoded.getPlayerSessionEpoch());
        assertEquals(11L, decoded.getClientRevision());
    }
}
