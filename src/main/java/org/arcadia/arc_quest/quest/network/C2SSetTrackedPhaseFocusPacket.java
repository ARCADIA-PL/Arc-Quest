package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.service.TrackedQuestService;
import org.arcadia.arc_quest.questmarker.runtime.QuestMarkerReconciliationService;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

import java.util.function.Supplier;

public record C2SSetTrackedPhaseFocusPacket(String questId, String phaseId) {

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

    public static void handle(C2SSetTrackedPhaseFocusPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
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
        context.setPacketHandled(true);
    }
}
