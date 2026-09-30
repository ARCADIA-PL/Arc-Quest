package org.arcadia.arc_quest.integration.jei.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** A short-lived read-only subscription; it never opens a business interaction/session. */
public record C2SJeiCatalogRequest(long nonce, long revision, long contentEpoch, boolean enabled) {
    public static void encode(C2SJeiCatalogRequest packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.nonce); buffer.writeLong(packet.revision);
        buffer.writeLong(packet.contentEpoch); buffer.writeBoolean(packet.enabled);
    }
    public static C2SJeiCatalogRequest decode(FriendlyByteBuf buffer) {
        return new C2SJeiCatalogRequest(buffer.readLong(), buffer.readLong(), buffer.readLong(), buffer.readBoolean());
    }
    public static void handle(C2SJeiCatalogRequest packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        if (context.getSender() != null) context.enqueueWork(() -> JeiCatalogService.subscribe(context.getSender(), packet));
        context.setPacketHandled(true);
    }
}
