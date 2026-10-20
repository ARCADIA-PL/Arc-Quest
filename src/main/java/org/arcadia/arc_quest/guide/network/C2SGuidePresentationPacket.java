package org.arcadia.arc_quest.guide.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.api.event.guide.GuidePresentationEvent;
import org.arcadia.arc_quest.guide.runtime.GuidePresentationService;
import java.util.UUID;
import java.util.function.Supplier;

public record C2SGuidePresentationPacket(ResourceLocation guide, UUID token, GuidePresentationEvent.Stage stage) {
    public static void encode(C2SGuidePresentationPacket packet, FriendlyByteBuf buf) {
        buf.writeResourceLocation(packet.guide); buf.writeUUID(packet.token); buf.writeEnum(packet.stage);
    }
    public static C2SGuidePresentationPacket decode(FriendlyByteBuf buf) {
        return new C2SGuidePresentationPacket(buf.readResourceLocation(), buf.readUUID(), buf.readEnum(GuidePresentationEvent.Stage.class));
    }
    public static void handle(C2SGuidePresentationPacket packet, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> {
            if (ctx.getSender() != null) GuidePresentationService.acknowledge(ctx.getSender(), packet.guide, packet.token, packet.stage);
        });
        ctx.setPacketHandled(true);
    }
}
