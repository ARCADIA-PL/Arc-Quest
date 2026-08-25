package org.arcadia.arc_quest.guide.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.api.event.guide.GuideCompletedEvent;
import org.arcadia.arc_quest.api.event.guide.GuideEvents;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.guide.runtime.GuidePlayerStateSyncService;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.quest.network.QuestSyncCoordinator;

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
            GuideDefinition guide = guideId == null ? null : GuideRegistry.get(guideId);
            if (guide == null) {
                return;
            }
            if (!data.isGuideUnlocked(guideId)) {
                return;
            }
            if (!hasReachedFinalPage(guide.getPageCount(), data.getGuideProgress(guideId))) {
                return;
            }
            if (data.markGuideSeen(guideId)) {
                NeoForge.EVENT_BUS.post(new GuideEvents.Seen(player, guideId, guide));
                NeoForge.EVENT_BUS.post(new GuideCompletedEvent(player, guideId));
                QuestSyncCoordinator.persistSnapshot(player, data);
                GuidePlayerStateSyncService.sync(player, data);
                data.clearDirty(ArcQuestPlayer.DirtyKind.GUIDE_STATE);
            }
        });
    }

    static boolean hasReachedFinalPage(int pageCount, int progressPage) {
        return Math.max(0, progressPage) >= Math.max(0, pageCount - 1);
    }
}
