package org.arcadia.arc_quest.dialogue.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class S2CDialogueTranscriptSnapshotPacket {

    private final UUID sessionId;
    private final List<S2CDialogueTranscriptDeltaPacket.Entry> entries;
    private final byte[] encodedPayload;

    public S2CDialogueTranscriptSnapshotPacket(UUID sessionId, List<S2CDialogueTranscriptDeltaPacket.Entry> entries) {
        this.sessionId = sessionId;
        if (entries.size() > DialogueTranscriptCodec.MAX_ENTRIES) {
            throw new IllegalArgumentException("Dialogue transcript exceeds entry limit");
        }
        this.entries = List.copyOf(entries);
        encodedPayload = DialogueTranscriptCodec.freeze(buffer -> {
            buffer.writeUUID(sessionId);
            buffer.writeVarInt(this.entries.size());
            for (var entry : this.entries) DialogueTranscriptCodec.writeEntry(buffer, entry);
        });
    }

    public static S2CDialogueTranscriptSnapshotPacket decode(FriendlyByteBuf buf) {
        DialogueTranscriptCodec.checkPacketSize(buf);
        UUID sid = buf.readUUID();
        int count = DialogueTranscriptCodec.readCount(buf);
        List<S2CDialogueTranscriptDeltaPacket.Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) entries.add(DialogueTranscriptCodec.readEntry(buf));
        return new S2CDialogueTranscriptSnapshotPacket(sid, entries);
    }

    public static void handle(S2CDialogueTranscriptSnapshotPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ClientHandler.handle(pkt, ctx);
    }

    private static final class ClientHandler {
        private static void handle(S2CDialogueTranscriptSnapshotPacket pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> ClientDialogueCache.INSTANCE.replaceTranscriptSnapshot(pkt.sessionId, pkt.entries));
            ctx.get().setPacketHandled(true);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBytes(encodedPayload);
    }
}
