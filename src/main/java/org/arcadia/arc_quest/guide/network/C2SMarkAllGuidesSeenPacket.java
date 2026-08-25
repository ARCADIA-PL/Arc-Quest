package org.arcadia.arc_quest.guide.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.common.NeoForge;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.api.event.guide.GuideEvents;
import org.arcadia.arc_quest.guide.runtime.GuidePlayerStateSyncService;
import org.arcadia.arc_quest.quest.network.QuestSyncCoordinator;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

import java.util.LinkedHashSet;
import java.util.Set;

public final class C2SMarkAllGuidesSeenPacket implements CustomPacketPayload {
    public static final Type<C2SMarkAllGuidesSeenPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "mark_all_guides_seen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SMarkAllGuidesSeenPacket> STREAM_CODEC =
            StreamCodec.ofMember(C2SMarkAllGuidesSeenPacket::encode, C2SMarkAllGuidesSeenPacket::decode);

    public static void encode(C2SMarkAllGuidesSeenPacket packet, FriendlyByteBuf buffer) {
    }

    public static C2SMarkAllGuidesSeenPacket decode(FriendlyByteBuf buffer) {
        return new C2SMarkAllGuidesSeenPacket();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SMarkAllGuidesSeenPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
            Set<ResourceLocation> newlySeen = new LinkedHashSet<>(data.getUnlockedGuides());
            newlySeen.removeAll(data.getSeenGuides());
            if (!data.markAllUnlockedGuidesSeen()) return;
            NeoForge.EVENT_BUS.post(new GuideEvents.MarkedAllSeen(player, newlySeen));
            QuestSyncCoordinator.persistSnapshot(player, data);
            GuidePlayerStateSyncService.sync(player, data);
            data.clearDirty(ArcQuestPlayer.DirtyKind.GUIDE_STATE);
        });
    }
}
