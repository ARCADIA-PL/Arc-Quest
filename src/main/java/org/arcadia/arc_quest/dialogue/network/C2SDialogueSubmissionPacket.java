package org.arcadia.arc_quest.dialogue.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.dialogue.runtime.DialogueSubmissionService;
import java.util.UUID;
import java.util.List;
import java.util.function.Supplier;

public record C2SDialogueSubmissionPacket(UUID sessionId, long epoch, UUID token, String entryId, int amount,
                                         boolean cancel, List<String> entryIds) {
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
    public static void handle(C2SDialogueSubmissionPacket packet, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> { if (ctx.getSender() != null) DialogueSubmissionService.handle(ctx.getSender(), packet); });
        ctx.setPacketHandled(true);
    }
}
