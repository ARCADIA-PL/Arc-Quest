package org.arcadia.arc_quest.dialogue.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.dialogue.runtime.DialogueSubmissionService;
import java.util.UUID;
import java.util.List;

public record C2SDialogueSubmissionPacket(UUID sessionId, long epoch, UUID token, String entryId, int amount,
                                         boolean cancel, List<String> entryIds) implements CustomPacketPayload {
    public static final Type<C2SDialogueSubmissionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("arc_quest", "dialogue_submission_request"));
    public static final StreamCodec<FriendlyByteBuf, C2SDialogueSubmissionPacket> STREAM_CODEC =
            StreamCodec.ofMember(C2SDialogueSubmissionPacket::encode, C2SDialogueSubmissionPacket::decode);

    @Override public Type<C2SDialogueSubmissionPacket> type() { return TYPE; }
    public C2SDialogueSubmissionPacket(UUID sessionId, long epoch, UUID token, String entryId, int amount, boolean cancel) {
        this(sessionId, epoch, token, entryId, amount, cancel, List.of());
    }
    public C2SDialogueSubmissionPacket {
        entryIds = List.copyOf(entryIds);
        if (entryIds.size() > 128) throw new IllegalArgumentException("Submission selection count");
    }
    public void encode(FriendlyByteBuf b) {
        b.writeUUID(sessionId); b.writeLong(epoch); b.writeUUID(token); b.writeUtf(entryId, 256);
        b.writeVarInt(amount); b.writeBoolean(cancel);
        b.writeCollection(entryIds, (buf, id) -> buf.writeUtf(id, 256));
    }
    public static C2SDialogueSubmissionPacket decode(FriendlyByteBuf b) {
        UUID session = b.readUUID(); long epoch = b.readLong(); UUID token = b.readUUID();
        String entry = b.readUtf(256); int amount = b.readVarInt(); boolean cancel = b.readBoolean();
        int count = b.readVarInt();
        if (count < 0 || count > 128) throw new IllegalArgumentException("Submission selection count");
        var ids = new java.util.ArrayList<String>(count);
        for (int i = 0; i < count; i++) ids.add(b.readUtf(256));
        return new C2SDialogueSubmissionPacket(session, epoch, token, entry, amount, cancel, ids);
    }
    public static void handle(C2SDialogueSubmissionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) DialogueSubmissionService.handle(player, packet);
        });
    }
}
