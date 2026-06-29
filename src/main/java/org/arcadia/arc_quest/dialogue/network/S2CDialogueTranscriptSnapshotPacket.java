package org.arcadia.arc_quest.dialogue.network;

import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class S2CDialogueTranscriptSnapshotPacket implements CustomPacketPayload {

    public static final Type<S2CDialogueTranscriptSnapshotPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "dialogue_transcript_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CDialogueTranscriptSnapshotPacket> STREAM_CODEC =
            StreamCodec.ofMember(S2CDialogueTranscriptSnapshotPacket::encode, S2CDialogueTranscriptSnapshotPacket::decode);

    private final UUID sessionId;
    private final List<S2CDialogueTranscriptDeltaPacket.Entry> entries;

    public S2CDialogueTranscriptSnapshotPacket(UUID sessionId, List<S2CDialogueTranscriptDeltaPacket.Entry> entries) {
        this.sessionId = sessionId;
        this.entries = entries;
    }

    public static S2CDialogueTranscriptSnapshotPacket decode(FriendlyByteBuf buf) {
        UUID sid = buf.readUUID();
        int n = buf.readVarInt();
        List<S2CDialogueTranscriptDeltaPacket.Entry> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            long ms = buf.readLong();
            String role = buf.readUtf();
            Component speaker = ComponentSerialization.STREAM_CODEC.decode((RegistryFriendlyByteBuf) buf);
            Component text = ComponentSerialization.STREAM_CODEC.decode((RegistryFriendlyByteBuf) buf);
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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CDialogueTranscriptSnapshotPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientDialogueCache.INSTANCE.replaceTranscriptSnapshot(pkt.sessionId, pkt.entries));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(sessionId);
        buf.writeVarInt(entries.size());
        for (S2CDialogueTranscriptDeltaPacket.Entry e : entries) {
            buf.writeLong(e.clientMs());
            buf.writeUtf(e.role());
            ComponentSerialization.STREAM_CODEC.encode((RegistryFriendlyByteBuf) buf, e.speaker() != null ? e.speaker() : Component.empty());
            ComponentSerialization.STREAM_CODEC.encode((RegistryFriendlyByteBuf) buf, e.text() != null ? e.text() : Component.empty());

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
