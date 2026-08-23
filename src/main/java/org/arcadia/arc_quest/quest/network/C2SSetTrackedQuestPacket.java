package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.service.TrackedQuestService;
import org.arcadia.arc_quest.quest.tracking.application.TrackedPhaseFocusService;
import org.arcadia.arc_quest.questmarker.runtime.QuestMarkerReconciliationService;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class C2SSetTrackedQuestPacket implements CustomPacketPayload {

    public static final Type<C2SSetTrackedQuestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "set_tracked_quest"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SSetTrackedQuestPacket> STREAM_CODEC =
            StreamCodec.ofMember(C2SSetTrackedQuestPacket::encode, C2SSetTrackedQuestPacket::decode);

    @Nullable
    private final String questId;
    public C2SSetTrackedQuestPacket(@Nullable String questId) {
        this.questId = questId;
    }

    public static void encode(C2SSetTrackedQuestPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.questId != null);
        if (packet.questId != null) buffer.writeUtf(packet.questId, 256);
    }

    public static C2SSetTrackedQuestPacket decode(FriendlyByteBuf buffer) {
        return new C2SSetTrackedQuestPacket(buffer.readBoolean() ? buffer.readUtf(256) : null);
    }

    public static void handle(C2SSetTrackedQuestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
            if (player != null) {
                TrackedPhaseFocusService.prepare(player.getUUID(), packet.questId, null);
                boolean changed = TrackedQuestService.setTrackedQuest(player, packet.questId);
                if (!changed) {
                    ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
                    QuestMarkerReconciliationService.reconcileTrackingPhaseMarkers(player, data, true);
                    if (data.isDirty()) QuestSyncCoordinator.persistAndSyncIfChanged(player, data);
                    else ArcQuestNetwork.syncTrackedQuest(player, data);
                }
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void clearPlayer(UUID playerId) {
        TrackedPhaseFocusService.clearPlayer(playerId);
    }

    public static void clear() {
        TrackedPhaseFocusService.clearAll();
    }
}
