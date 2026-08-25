package org.arcadia.arc_quest.guide.network;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;

import java.util.function.Supplier;

public final class S2COpenGuidePacket {

    private final ResourceLocation guideId;
    private final int initialPage;
    private final boolean markSeenOnClose;

    public S2COpenGuidePacket(ResourceLocation guideId, int initialPage, boolean markSeenOnClose) {
        this.guideId = guideId;
        this.initialPage = Math.max(0, initialPage);
        this.markSeenOnClose = markSeenOnClose;
    }

    public static void encode(S2COpenGuidePacket pkt, FriendlyByteBuf buf) {
        buf.writeResourceLocation(pkt.guideId);
        buf.writeVarInt(pkt.initialPage);
        buf.writeBoolean(pkt.markSeenOnClose);
    }

    public static S2COpenGuidePacket decode(FriendlyByteBuf buf) {
        return new S2COpenGuidePacket(
                buf.readResourceLocation(),
                buf.readVarInt(),
                buf.readBoolean()
        );
    }

    public static void handle(S2COpenGuidePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (GuideRegistry.get(pkt.guideId) == null) {
                ArcQuestLog.warn(ArcQuestLog.Category.GUIDE, "Ignored open packet for unknown guide '{}'", pkt.guideId);
                return;
            }
            ClientGuideCache.INSTANCE.requestOpen(pkt.guideId, pkt.initialPage, pkt.markSeenOnClose);
        });
        ctx.get().setPacketHandled(true);
    }
}
