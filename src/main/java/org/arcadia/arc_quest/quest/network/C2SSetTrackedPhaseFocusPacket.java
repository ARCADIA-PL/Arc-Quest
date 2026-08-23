package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.service.TrackedQuestService;
import org.arcadia.arc_quest.questmarker.runtime.QuestMarkerReconciliationService;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

public record C2SSetTrackedPhaseFocusPacket(String questId, String phaseId) implements CustomPacketPayload {

    public static final Type<C2SSetTrackedPhaseFocusPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "set_tracked_phase_focus"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SSetTrackedPhaseFocusPacket> STREAM_CODEC =
            StreamCodec.ofMember(C2SSetTrackedPhaseFocusPacket::encode, C2SSetTrackedPhaseFocusPacket::decode);

    public C2SSetTrackedPhaseFocusPacket {
        if (questId == null || questId.isBlank()) throw new IllegalArgumentException("questId cannot be blank");
        if (phaseId == null || phaseId.isBlank()) throw new IllegalArgumentException("phaseId cannot be blank");
    }

    public static void encode(C2SSetTrackedPhaseFocusPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.questId, 256);
        buffer.writeUtf(packet.phaseId, 256);
    }

    public static C2SSetTrackedPhaseFocusPacket decode(FriendlyByteBuf buffer) {
        return new C2SSetTrackedPhaseFocusPacket(buffer.readUtf(256), buffer.readUtf(256));
    }

    public static void handle(C2SSetTrackedPhaseFocusPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
            if (player == null) return;

            ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
            ResourceLocation questKey = ResourceLocation.tryParse(packet.questId);
            QuestRuntimeData runtime = data.getActiveQuest(packet.questId);
            QuestDefinition definition = questKey == null ? null : QuestRegistry.get(questKey);
            if (runtime == null || runtime.getState() != QuestState.ACTIVE || definition == null
                    || definition.getPhase(packet.phaseId) == null || !runtime.isPhaseActive(packet.phaseId)) {
                ArcQuestNetwork.syncTrackedQuest(player, data);
                return;
            }

            TrackedQuestService.setTrackedQuest(player, packet.questId);
            data = ArcQuestPlayerManager.getOrCreate(player);
            if (!packet.questId.equals(data.getTrackedQuestId())) return;
            data.setTrackedPhaseId(packet.phaseId);
            QuestMarkerReconciliationService.reconcileTrackingPhaseMarkers(player, data, true);
            if (data.isDirty()) QuestSyncCoordinator.persistAndSyncIfChanged(player, data);
            else ArcQuestNetwork.syncTrackedQuest(player, data);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
