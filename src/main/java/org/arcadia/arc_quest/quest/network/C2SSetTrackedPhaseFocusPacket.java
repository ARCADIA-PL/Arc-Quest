package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.quest.service.TrackedQuestService;
import org.arcadia.arc_quest.quest.tracking.application.TrackedPhaseFocusService;
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

            TrackedPhaseFocusService.prepare(player.getUUID(), packet.questId, packet.phaseId);
            boolean changed = TrackedQuestService.setTrackedQuest(player, packet.questId);
            if (changed) return;

            ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
            QuestMarkerReconciliationService.reconcileTrackingPhaseMarkers(player, data, true);
            if (data.isDirty()) QuestSyncCoordinator.persistAndSyncIfChanged(player, data);
            else ArcQuestNetwork.syncTrackedQuest(player, data);
        });
        context.setPacketHandled(true);
    }
}
