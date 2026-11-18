package org.arcadia.arc_quest.guide.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.api.event.guide.GuidePresentationEvent;
import org.arcadia.arc_quest.guide.runtime.GuidePresentationService;
import java.util.UUID;

public record C2SGuidePresentationPacket(ResourceLocation guide, UUID token, GuidePresentationEvent.Stage stage) implements CustomPacketPayload {
    public static final Type<C2SGuidePresentationPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("arc_quest", "guide_presentation_ack"));
    public static final StreamCodec<FriendlyByteBuf, C2SGuidePresentationPacket> STREAM_CODEC =
            StreamCodec.ofMember(C2SGuidePresentationPacket::encode, C2SGuidePresentationPacket::decode);

    @Override public Type<C2SGuidePresentationPacket> type() { return TYPE; }
    public static void encode(C2SGuidePresentationPacket packet, FriendlyByteBuf buf) {
        buf.writeResourceLocation(packet.guide); buf.writeUUID(packet.token); buf.writeEnum(packet.stage);
    }
    public static C2SGuidePresentationPacket decode(FriendlyByteBuf buf) {
        return new C2SGuidePresentationPacket(buf.readResourceLocation(), buf.readUUID(), buf.readEnum(GuidePresentationEvent.Stage.class));
    }
    public static void handle(C2SGuidePresentationPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) GuidePresentationService.acknowledge(player, packet.guide, packet.token, packet.stage);
        });
    }
}
