package org.arcadia.arc_quest.dialogue.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;

import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import java.util.function.Supplier;

public class S2CDialogueTranscriptDeltaPacket implements CustomPacketPayload {
    public static final Type<S2CDialogueTranscriptDeltaPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "dialogue_transcript_delta"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CDialogueTranscriptDeltaPacket> STREAM_CODEC = StreamCodec.ofMember(S2CDialogueTranscriptDeltaPacket::encode, S2CDialogueTranscriptDeltaPacket::decode);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }


    private final UUID sessionId;
    private final Entry entry;
    private final byte[] encodedPayload;

    public S2CDialogueTranscriptDeltaPacket(UUID sessionId, Entry entry) {
        this(sessionId, entry, RegistryAccess.EMPTY);
    }

    public S2CDialogueTranscriptDeltaPacket(UUID sessionId, Entry entry, HolderLookup.Provider registries) {
        this.sessionId = sessionId;
        this.entry = entry;
        encodedPayload = DialogueTranscriptCodec.freeze(buffer -> {
            buffer.writeUUID(sessionId);
            DialogueTranscriptCodec.writeEntry(buffer, entry, registries);
        });
    }

    public static S2CDialogueTranscriptDeltaPacket decode(FriendlyByteBuf buf) {
        DialogueTranscriptCodec.checkPacketSize(buf);
        UUID sid = buf.readUUID();
        return new S2CDialogueTranscriptDeltaPacket(sid, DialogueTranscriptCodec.readEntry(buf), DialogueTranscriptCodec.registries(buf));
    }

    public static void handle(S2CDialogueTranscriptDeltaPacket pkt, IPayloadContext ctx) {
        ClientHandler.handle(pkt, ctx);
    }

    private static final class ClientHandler {
        private static void handle(S2CDialogueTranscriptDeltaPacket pkt, IPayloadContext ctx) {
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
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBytes(encodedPayload);
    }

    public record Entry(long clientMs, String role, Component speaker, Component text,
                        @Nullable String nodeId, @Nullable String sayId,
                        @Nullable String choiceId, int choiceIndexOrNeg1) {
    }
}