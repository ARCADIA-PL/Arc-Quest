package org.arcadia.arc_quest.dialogue.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class S2CDialogueTranscriptSnapshotPacket {

    private final UUID sessionId;
    private final List<S2CDialogueTranscriptDeltaPacket.Entry> entries;

    public S2CDialogueTranscriptSnapshotPacket(UUID sessionId, List<S2CDialogueTranscriptDeltaPacket.Entry> entries) {
        this.sessionId = sessionId;
        this.entries = List.copyOf(entries);
    }

    public static S2CDialogueTranscriptSnapshotPacket decode(FriendlyByteBuf buf) {
        UUID sid = buf.readUUID();
        int n = buf.readVarInt();
        List<S2CDialogueTranscriptDeltaPacket.Entry> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            long ms = buf.readLong();
            String role = buf.readUtf();
            Component speaker = buf.readComponent();
            Component text = buf.readComponent();
            String nodeId = buf.readBoolean() ? buf.readUtf() : null;
            String sayId = buf.readBoolean() ? buf.readUtf() : null;
            String choiceId = buf.readBoolean() ? buf.readUtf() : null;
            int choiceIndexOrNeg1 = buf.readVarInt();
            entries.add(new S2CDialogueTranscriptDeltaPacket.Entry(
                    ms, role, speaker, text, nodeId, sayId, choiceId, choiceIndexOrNeg1
            ));
        }
        return new S2CDialogueTranscriptSnapshotPacket(sid, entries);
    }

    public static void handle(S2CDialogueTranscriptSnapshotPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientDialogueCache.INSTANCE.replaceTranscriptSnapshot(pkt.sessionId, pkt.entries));
        ctx.get().setPacketHandled(true);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(sessionId);
        buf.writeVarInt(entries.size());
        for (S2CDialogueTranscriptDeltaPacket.Entry e : entries) {
            buf.writeLong(e.clientMs());
            buf.writeUtf(e.role());
            buf.writeComponent(e.speaker() != null ? e.speaker() : Component.empty());
            buf.writeComponent(e.text() != null ? e.text() : Component.empty());

            if (e.nodeId() != null) {
                buf.writeBoolean(true);
                buf.writeUtf(e.nodeId());
            } else buf.writeBoolean(false);

            if (e.sayId() != null) {
                buf.writeBoolean(true);
                buf.writeUtf(e.sayId());
            } else buf.writeBoolean(false);

            if (e.choiceId() != null) {
                buf.writeBoolean(true);
                buf.writeUtf(e.choiceId());
            } else buf.writeBoolean(false);

            buf.writeVarInt(e.choiceIndexOrNeg1());
        }
    }
}