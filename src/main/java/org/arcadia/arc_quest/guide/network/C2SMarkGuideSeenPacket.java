package org.arcadia.arc_quest.guide.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.guide.runtime.GuidePlayerStateSyncService;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

public final class C2SMarkGuideSeenPacket implements CustomPacketPayload {

    public static final Type<C2SMarkGuideSeenPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "mark_guide_seen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SMarkGuideSeenPacket> STREAM_CODEC =
            StreamCodec.ofMember(C2SMarkGuideSeenPacket::encode, C2SMarkGuideSeenPacket::decode);

    private final String guideId;

    public C2SMarkGuideSeenPacket(String guideId) {
        this.guideId = guideId;
    }

    public static void encode(C2SMarkGuideSeenPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.guideId);
    }

    public static C2SMarkGuideSeenPacket decode(FriendlyByteBuf buf) {
        return new C2SMarkGuideSeenPacket(buf.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SMarkGuideSeenPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
            ResourceLocation guideId = ResourceLocation.tryParse(pkt.guideId);
            if (guideId == null || GuideRegistry.get(guideId) == null) {
                return;
            }
            if (!data.isGuideUnlocked(guideId)) {
                return;
            }
            if (data.markGuideSeen(guideId)) {
                GuidePlayerStateSyncService.sync(player, data);
            }
        });
    }
}
