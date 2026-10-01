package org.arcadia.arc_quest.dialogue.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;


import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import java.util.function.Supplier;

public class S2CDialogueTranscriptSnapshotPacket implements CustomPacketPayload {
    public static final Type<S2CDialogueTranscriptSnapshotPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "dialogue_transcript_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CDialogueTranscriptSnapshotPacket> STREAM_CODEC = StreamCodec.ofMember(S2CDialogueTranscriptSnapshotPacket::encode, S2CDialogueTranscriptSnapshotPacket::decode);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }


    private final UUID sessionId;
    private final List<S2CDialogueTranscriptDeltaPacket.Entry> entries;
    private final byte[] encodedPayload;

    public S2CDialogueTranscriptSnapshotPacket(UUID sessionId, List<S2CDialogueTranscriptDeltaPacket.Entry> entries) {
        this(sessionId, entries, RegistryAccess.EMPTY);
    }

    public S2CDialogueTranscriptSnapshotPacket(UUID sessionId, List<S2CDialogueTranscriptDeltaPacket.Entry> entries, HolderLookup.Provider registries) {
        this.sessionId = sessionId;
        if (entries.size() > DialogueTranscriptCodec.MAX_ENTRIES) {
            throw new IllegalArgumentException("Dialogue transcript exceeds entry limit");
        }
        this.entries = List.copyOf(entries);
        encodedPayload = DialogueTranscriptCodec.freeze(buffer -> {
            buffer.writeUUID(sessionId);
            buffer.writeVarInt(this.entries.size());
            for (var entry : this.entries) DialogueTranscriptCodec.writeEntry(buffer, entry, registries);
        });
    }

    public static S2CDialogueTranscriptSnapshotPacket decode(FriendlyByteBuf buf) {
        DialogueTranscriptCodec.checkPacketSize(buf);
        UUID sid = buf.readUUID();
        int count = DialogueTranscriptCodec.readCount(buf);
        List<S2CDialogueTranscriptDeltaPacket.Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) entries.add(DialogueTranscriptCodec.readEntry(buf));
        return new S2CDialogueTranscriptSnapshotPacket(sid, entries, DialogueTranscriptCodec.registries(buf));
    }

    public static void handle(S2CDialogueTranscriptSnapshotPacket pkt, IPayloadContext ctx) {
        ClientHandler.handle(pkt, ctx);
    }

    private static final class ClientHandler {
        private static void handle(S2CDialogueTranscriptSnapshotPacket pkt, IPayloadContext ctx) {
            ctx.enqueueWork(() -> ClientDialogueCache.INSTANCE.replaceTranscriptSnapshot(pkt.sessionId, pkt.entries));

        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBytes(encodedPayload);
    }
}
