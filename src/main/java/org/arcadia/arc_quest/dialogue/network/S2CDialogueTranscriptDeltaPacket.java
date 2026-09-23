package org.arcadia.arc_quest.dialogue.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

public class S2CDialogueTranscriptDeltaPacket {

    private final UUID sessionId;
    private final Entry entry;
    private final byte[] encodedPayload;

    public S2CDialogueTranscriptDeltaPacket(UUID sessionId, Entry entry) {
        this.sessionId = sessionId;
        this.entry = entry;
        encodedPayload = DialogueTranscriptCodec.freeze(buffer -> {
            buffer.writeUUID(sessionId);
            DialogueTranscriptCodec.writeEntry(buffer, entry);
        });
    }

    public static S2CDialogueTranscriptDeltaPacket decode(FriendlyByteBuf buf) {
        DialogueTranscriptCodec.checkPacketSize(buf);
        UUID sid = buf.readUUID();
        return new S2CDialogueTranscriptDeltaPacket(sid, DialogueTranscriptCodec.readEntry(buf));
    }

    public static void handle(S2CDialogueTranscriptDeltaPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ClientHandler.handle(pkt, ctx);
    }

    private static final class ClientHandler {
        private static void handle(S2CDialogueTranscriptDeltaPacket pkt, Supplier<NetworkEvent.Context> ctx) {
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

    public void encode(FriendlyByteBuf buf) {
        buf.writeBytes(encodedPayload);
    }

    public record Entry(long clientMs, String role, Component speaker, Component text,
                        @Nullable String nodeId, @Nullable String sayId,
                        @Nullable String choiceId, int choiceIndexOrNeg1) {
    }
}