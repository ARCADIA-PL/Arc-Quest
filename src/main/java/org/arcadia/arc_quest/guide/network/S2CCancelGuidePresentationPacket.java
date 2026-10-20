package org.arcadia.arc_quest.guide.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

/** Cancels only the server-issued presentation, leaving unrelated Guide screens untouched. */
public record S2CCancelGuidePresentationPacket(UUID token) {
    public static void encode(S2CCancelGuidePresentationPacket packet, FriendlyByteBuf buffer) { buffer.writeUUID(packet.token); }
    public static S2CCancelGuidePresentationPacket decode(FriendlyByteBuf buffer) { return new S2CCancelGuidePresentationPacket(buffer.readUUID()); }
    public static void handle(S2CCancelGuidePresentationPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ClientGuideCache.INSTANCE.cancelPresentation(packet.token);
            org.arcadia.arc_quest.client.hud.guide.GuideScreen.cancelPresentation(packet.token);
        });
        context.get().setPacketHandled(true);
    }
}
