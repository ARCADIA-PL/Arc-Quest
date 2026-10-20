package org.arcadia.arc_quest.guide.network;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;

import java.util.function.Supplier;

public final class S2COpenGuidePacket {

    private final java.util.UUID token;
    private final ResourceLocation guideId;
    private final int initialPage;
    private final boolean markSeenOnClose;

    public S2COpenGuidePacket(ResourceLocation guideId, int initialPage, boolean markSeenOnClose) {
        this(guideId, initialPage, markSeenOnClose, null);
    }

    public S2COpenGuidePacket(ResourceLocation guideId, int initialPage, boolean markSeenOnClose, java.util.UUID token) {
        this.token = token;
        this.guideId = guideId;
        this.initialPage = Math.max(0, initialPage);
        this.markSeenOnClose = markSeenOnClose;
    }

    public static void encode(S2COpenGuidePacket pkt, FriendlyByteBuf buf) {
        buf.writeResourceLocation(pkt.guideId);
        buf.writeVarInt(pkt.initialPage);
        buf.writeBoolean(pkt.markSeenOnClose);
        buf.writeBoolean(pkt.token != null);
        if (pkt.token != null) buf.writeUUID(pkt.token);
    }

    public static S2COpenGuidePacket decode(FriendlyByteBuf buf) {
        return new S2COpenGuidePacket(
                buf.readResourceLocation(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readBoolean() ? buf.readUUID() : null
        );
    }

    public static void handle(S2COpenGuidePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (GuideRegistry.get(pkt.guideId) == null) {
                ArcQuestLog.warn(ArcQuestLog.Category.GUIDE, "Ignored open packet for unknown guide '{}'", pkt.guideId);
                return;
            }
            ClientGuideCache.INSTANCE.requestOpen(pkt.guideId, pkt.initialPage, pkt.markSeenOnClose, pkt.token);
        });
        ctx.get().setPacketHandled(true);
    }
}
