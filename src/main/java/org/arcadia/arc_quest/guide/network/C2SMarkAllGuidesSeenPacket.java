package org.arcadia.arc_quest.guide.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.guide.runtime.GuidePlayerStateSyncService;
import org.arcadia.arc_quest.quest.network.QuestSyncCoordinator;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

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
            if (!data.markAllUnlockedGuidesSeen()) return;
            QuestSyncCoordinator.persistSnapshot(player, data);
            GuidePlayerStateSyncService.sync(player, data);
            data.clearDirty(ArcQuestPlayer.DirtyKind.GUIDE_STATE);
        });
        context.setPacketHandled(true);
    }
}
