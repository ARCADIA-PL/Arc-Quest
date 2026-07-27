package org.arcadia.arc_quest.guide.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.quest.network.QuestSyncCoordinator;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

import java.util.function.Supplier;

public record C2SUpdateGuideProgressPacket(ResourceLocation guideId, int pageIndex) {

    public C2SUpdateGuideProgressPacket {
        pageIndex = Math.max(0, pageIndex);
    }

    public static void encode(C2SUpdateGuideProgressPacket packet, FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(packet.guideId);
        buffer.writeVarInt(packet.pageIndex);
    }

    public static C2SUpdateGuideProgressPacket decode(FriendlyByteBuf buffer) {
        return new C2SUpdateGuideProgressPacket(buffer.readResourceLocation(), buffer.readVarInt());
    }

    public static void handle(C2SUpdateGuideProgressPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            GuideDefinition guide = GuideRegistry.get(packet.guideId);
            if (guide == null) return;
            ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
            if (!data.isGuideUnlocked(packet.guideId)) return;
            int page = Math.min(packet.pageIndex, Math.max(0, guide.getPageCount() - 1));
            if (data.setGuideProgress(packet.guideId, page)) {
                QuestSyncCoordinator.persistSnapshot(player, data);
                data.clearDirty(ArcQuestPlayer.DirtyKind.GUIDE_STATE);
            }
        });
        context.setPacketHandled(true);
    }
}
