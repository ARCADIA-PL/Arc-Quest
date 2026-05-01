package org.arcadia.arc_quest.dialogue.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

public class S2CDialogueTranscriptDeltaPacket {

    public record Entry(long clientMs, String role, String speaker, String text,
                        @Nullable String nodeId, @Nullable String sayId,
                        @Nullable String choiceId, int choiceIndexOrNeg1) {
    }

    private final UUID sessionId;
    private final Entry entry;

    public S2CDialogueTranscriptDeltaPacket(UUID sessionId, Entry entry) {
        this.sessionId = sessionId;
        this.entry = entry;
    }

    public static S2CDialogueTranscriptDeltaPacket decode(FriendlyByteBuf buf) {
        UUID sid = buf.readUUID();
        long ms = buf.readLong();
        String role = buf.readUtf();
        String speaker = buf.readUtf();
        String text = buf.readUtf(4096);
        String nodeId = buf.readBoolean() ? buf.readUtf() : null;
        String sayId = buf.readBoolean() ? buf.readUtf() : null;
        String choiceId = buf.readBoolean() ? buf.readUtf() : null;
        int choiceIndexOrNeg1 = buf.readVarInt();
        return new S2CDialogueTranscriptDeltaPacket(
                sid,
                new Entry(ms, role, speaker, text, nodeId, sayId, choiceId, choiceIndexOrNeg1)
        );
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(sessionId);
        buf.writeLong(entry.clientMs());
        buf.writeUtf(entry.role());
        buf.writeUtf(entry.speaker() != null ? entry.speaker() : "");
        buf.writeUtf(entry.text() != null ? entry.text() : "", 4096);

        if (entry.nodeId() != null) {
            buf.writeBoolean(true);
            buf.writeUtf(entry.nodeId());
        } else buf.writeBoolean(false);

        if (entry.sayId() != null) {
            buf.writeBoolean(true);
            buf.writeUtf(entry.sayId());
        } else buf.writeBoolean(false);

        if (entry.choiceId() != null) {
            buf.writeBoolean(true);
            buf.writeUtf(entry.choiceId());
        } else buf.writeBoolean(false);

        buf.writeVarInt(entry.choiceIndexOrNeg1());
    }

    public static void handle(S2CDialogueTranscriptDeltaPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientDialogueCache.INSTANCE.appendTranscriptEntry(
                pkt.sessionId,
                pkt.entry.clientMs(),
                pkt.entry.role(),
                pkt.entry.speaker(),
                pkt.entry.text(),
                pkt.entry.nodeId(),
                pkt.entry.sayId(),
                pkt.entry.choiceId(),
                pkt.entry.choiceIndexOrNeg1() >= 0 ? pkt.entry.choiceIndexOrNeg1() : null
        ));
        ctx.get().setPacketHandled(true);
    }
}