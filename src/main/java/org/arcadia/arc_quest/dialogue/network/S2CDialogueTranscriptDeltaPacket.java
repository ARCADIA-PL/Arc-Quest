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

import javax.annotation.Nullable;
import java.util.UUID;

public final class S2CDialogueTranscriptDeltaPacket implements CustomPacketPayload {

    public static final Type<S2CDialogueTranscriptDeltaPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "dialogue_transcript_delta"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CDialogueTranscriptDeltaPacket> STREAM_CODEC =
            StreamCodec.ofMember(S2CDialogueTranscriptDeltaPacket::encode, S2CDialogueTranscriptDeltaPacket::decode);

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
        Component speaker = ComponentSerialization.STREAM_CODEC.decode((RegistryFriendlyByteBuf) buf);
        Component text = ComponentSerialization.STREAM_CODEC.decode((RegistryFriendlyByteBuf) buf);
        String nodeId = buf.readBoolean() ? buf.readUtf() : null;
        String sayId = buf.readBoolean() ? buf.readUtf() : null;
        String choiceId = buf.readBoolean() ? buf.readUtf() : null;
        int choiceIndexOrNeg1 = buf.readVarInt();
        return new S2CDialogueTranscriptDeltaPacket(
                sid,
                new Entry(ms, role, speaker, text, nodeId, sayId, choiceId, choiceIndexOrNeg1)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CDialogueTranscriptDeltaPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientDialogueCache.INSTANCE.appendTranscriptEntry(
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
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(sessionId);
        buf.writeLong(entry.clientMs());
        buf.writeUtf(entry.role());
        ComponentSerialization.STREAM_CODEC.encode((RegistryFriendlyByteBuf) buf, entry.speaker() != null ? entry.speaker() : Component.empty());
        ComponentSerialization.STREAM_CODEC.encode((RegistryFriendlyByteBuf) buf, entry.text() != null ? entry.text() : Component.empty());

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

    public record Entry(long clientMs, String role, Component speaker, Component text,
                        @Nullable String nodeId, @Nullable String sayId,
                        @Nullable String choiceId, int choiceIndexOrNeg1) {
    }
}
