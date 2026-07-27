package org.arcadia.arc_quest.guide.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.guide.runtime.GuidePlayerStateSyncService;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.quest.network.QuestSyncCoordinator;

import java.util.function.Supplier;

public final class C2SMarkGuideSeenPacket {

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

    public static void handle(C2SMarkGuideSeenPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
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
                QuestSyncCoordinator.persistSnapshot(player, data);
                GuidePlayerStateSyncService.sync(player, data);
                data.clearDirty(ArcQuestPlayer.DirtyKind.GUIDE_STATE);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
