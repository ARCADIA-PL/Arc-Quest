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
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.quest.network.QuestSyncCoordinator;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

public record C2SUpdateGuideProgressPacket(ResourceLocation guideId, int pageIndex)
        implements CustomPacketPayload {

    public static final Type<C2SUpdateGuideProgressPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "update_guide_progress"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SUpdateGuideProgressPacket> STREAM_CODEC =
            StreamCodec.ofMember(C2SUpdateGuideProgressPacket::encode, C2SUpdateGuideProgressPacket::decode);

    public C2SUpdateGuideProgressPacket {
        pageIndex = Math.max(0, pageIndex);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(guideId);
        buffer.writeVarInt(pageIndex);
    }

    public static C2SUpdateGuideProgressPacket decode(FriendlyByteBuf buffer) {
        return new C2SUpdateGuideProgressPacket(buffer.readResourceLocation(), buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SUpdateGuideProgressPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            GuideDefinition guide = GuideRegistry.get(packet.guideId);
            if (guide == null) return;
            ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
            if (!data.isGuideUnlocked(packet.guideId)) return;
            int page = Math.min(packet.pageIndex, Math.max(0, guide.getPageCount() - 1));
            int oldPage = data.getGuideProgress(packet.guideId);
            if (data.setGuideProgress(packet.guideId, page)) {
                NeoForge.EVENT_BUS.post(new GuideEvents.ProgressChanged(
                        player, packet.guideId, guide, oldPage, page));
                QuestSyncCoordinator.persistSnapshot(player, data);
                data.clearDirty(ArcQuestPlayer.DirtyKind.GUIDE_STATE);
            }
        });
    }
}
