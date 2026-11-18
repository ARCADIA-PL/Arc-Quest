package org.arcadia.arc_quest.guide.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.UUID;

/** Cancels only the server-issued presentation, leaving unrelated Guide screens untouched. */
public record S2CCancelGuidePresentationPacket(UUID token) implements CustomPacketPayload {
    public static final Type<S2CCancelGuidePresentationPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("arc_quest", "guide_presentation_cancel"));
    public static final StreamCodec<FriendlyByteBuf, S2CCancelGuidePresentationPacket> STREAM_CODEC =
            StreamCodec.ofMember(S2CCancelGuidePresentationPacket::encode, S2CCancelGuidePresentationPacket::decode);

    @Override public Type<S2CCancelGuidePresentationPacket> type() { return TYPE; }
    public static void encode(S2CCancelGuidePresentationPacket packet, FriendlyByteBuf buffer) { buffer.writeUUID(packet.token); }
    public static S2CCancelGuidePresentationPacket decode(FriendlyByteBuf buffer) { return new S2CCancelGuidePresentationPacket(buffer.readUUID()); }
    public static void handle(S2CCancelGuidePresentationPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientGuideCache.INSTANCE.cancelPresentation(packet.token);
            org.arcadia.arc_quest.client.hud.guide.GuideScreen.cancelPresentation(packet.token);
        });
    }
}
