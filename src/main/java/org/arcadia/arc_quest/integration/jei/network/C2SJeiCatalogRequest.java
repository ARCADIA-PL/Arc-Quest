package org.arcadia.arc_quest.integration.jei.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;


/** A short-lived read-only subscription; it never opens a business interaction/session. */
public record C2SJeiCatalogRequest(long nonce, long revision, long contentEpoch, boolean enabled) implements CustomPacketPayload {
    public static final Type<C2SJeiCatalogRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "jei_catalog_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SJeiCatalogRequest> STREAM_CODEC = StreamCodec.ofMember(C2SJeiCatalogRequest::encode, C2SJeiCatalogRequest::decode);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(C2SJeiCatalogRequest packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.nonce); buffer.writeLong(packet.revision);
        buffer.writeLong(packet.contentEpoch); buffer.writeBoolean(packet.enabled);
    }
    public static C2SJeiCatalogRequest decode(FriendlyByteBuf buffer) {
        return new C2SJeiCatalogRequest(buffer.readLong(), buffer.readLong(), buffer.readLong(), buffer.readBoolean());
    }
    public static void handle(C2SJeiCatalogRequest packet, IPayloadContext context) {
        if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
            context.enqueueWork(() -> JeiCatalogService.subscribe(player, packet));
        }
    }
}
