package org.arcadia.arc_quest.dialogue.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.dialogue.runtime.DialogueSubmissionService;
import java.util.UUID;
import java.util.function.Supplier;

public record C2SDialogueSubmissionPacket(UUID sessionId, long epoch, UUID token, String entryId, int amount, boolean cancel) {
    public void encode(FriendlyByteBuf b) {
        b.writeUUID(sessionId); b.writeLong(epoch); b.writeUUID(token); b.writeUtf(entryId, 256);
        b.writeVarInt(amount); b.writeBoolean(cancel);
    }
    public static C2SDialogueSubmissionPacket decode(FriendlyByteBuf b) {
        return new C2SDialogueSubmissionPacket(b.readUUID(), b.readLong(), b.readUUID(), b.readUtf(256), b.readVarInt(), b.readBoolean());
    }
    public static void handle(C2SDialogueSubmissionPacket packet, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> { if (ctx.getSender() != null) DialogueSubmissionService.handle(ctx.getSender(), packet); });
        ctx.setPacketHandled(true);
    }
}
