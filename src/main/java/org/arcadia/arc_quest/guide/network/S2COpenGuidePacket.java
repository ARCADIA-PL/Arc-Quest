package org.arcadia.arc_quest.guide.network;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;

public final class S2COpenGuidePacket implements CustomPacketPayload {
    public static final Type<S2COpenGuidePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "open_guide"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2COpenGuidePacket> STREAM_CODEC =
            StreamCodec.ofMember(S2COpenGuidePacket::encode, S2COpenGuidePacket::decode);

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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2COpenGuidePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (GuideRegistry.get(pkt.guideId) == null) {
                ArcQuestLog.warn(ArcQuestLog.Category.GUIDE, "Ignored open packet for unknown guide '{}'", pkt.guideId);
                return;
            }
            ClientGuideCache.INSTANCE.requestOpen(pkt.guideId, pkt.initialPage, pkt.markSeenOnClose, pkt.token);
        });
    }
}
