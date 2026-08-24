package org.arcadia.arc_quest.guide.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.guide.runtime.GuidePlayerStateSyncService;
import org.arcadia.arc_quest.quest.network.QuestSyncCoordinator;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

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
            if (!data.markAllUnlockedGuidesSeen()) return;
            QuestSyncCoordinator.persistSnapshot(player, data);
            GuidePlayerStateSyncService.sync(player, data);
            data.clearDirty(ArcQuestPlayer.DirtyKind.GUIDE_STATE);
        });
    }
}
