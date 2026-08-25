package org.arcadia.arc_quest.guide.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.api.event.guide.GuideEvents;
import org.arcadia.arc_quest.guide.runtime.GuidePlayerStateSyncService;
import org.arcadia.arc_quest.quest.network.QuestSyncCoordinator;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

public final class C2SMarkAllGuidesSeenPacket {

    public static void encode(C2SMarkAllGuidesSeenPacket packet, FriendlyByteBuf buffer) {
    }

    public static C2SMarkAllGuidesSeenPacket decode(FriendlyByteBuf buffer) {
        return new C2SMarkAllGuidesSeenPacket();
    }

    public static void handle(C2SMarkAllGuidesSeenPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
            Set<ResourceLocation> newlySeen = new LinkedHashSet<>(data.getUnlockedGuides());
            newlySeen.removeAll(data.getSeenGuides());
            if (!data.markAllUnlockedGuidesSeen()) return;
            MinecraftForge.EVENT_BUS.post(new GuideEvents.MarkedAllSeen(player, newlySeen));
            QuestSyncCoordinator.persistSnapshot(player, data);
            GuidePlayerStateSyncService.sync(player, data);
            data.clearDirty(ArcQuestPlayer.DirtyKind.GUIDE_STATE);
        });
        context.setPacketHandled(true);
    }
}
