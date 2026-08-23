package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.quest.service.TrackedQuestService;
import org.arcadia.arc_quest.quest.tracking.application.TrackedPhaseFocusService;
import org.arcadia.arc_quest.questmarker.runtime.QuestMarkerReconciliationService;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Supplier;

public final class C2SSetTrackedQuestPacket {

    @Nullable
    private final String questId;
    @Nullable
    private final String phaseId;

    public C2SSetTrackedQuestPacket(@Nullable String questId) {
        this(questId, null);
    }

    public C2SSetTrackedQuestPacket(@Nullable String questId, @Nullable String phaseId) {
        this.questId = questId;
        this.phaseId = phaseId;
    }

    public static void encode(C2SSetTrackedQuestPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.questId != null);
        if (packet.questId != null) buffer.writeUtf(packet.questId, 256);
        buffer.writeBoolean(packet.phaseId != null);
        if (packet.phaseId != null) buffer.writeUtf(packet.phaseId, 256);
    }

    public static C2SSetTrackedQuestPacket decode(FriendlyByteBuf buffer) {
        String questId = buffer.readBoolean() ? buffer.readUtf(256) : null;
        String phaseId = buffer.readBoolean() ? buffer.readUtf(256) : null;
        return new C2SSetTrackedQuestPacket(questId, phaseId);
    }

    @Nullable
    String questId() {
        return questId;
    }

    @Nullable
    String phaseId() {
        return phaseId;
    }

    public static void handle(C2SSetTrackedQuestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                TrackedPhaseFocusService.prepare(player.getUUID(), packet.questId, packet.phaseId);
                boolean changed = TrackedQuestService.setTrackedQuest(player, packet.questId);
                if (!changed) {
                    ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
                    QuestMarkerReconciliationService.reconcileTrackingPhaseMarkers(player, data, true);
                    if (data.isDirty()) QuestSyncCoordinator.persistAndSyncIfChanged(player, data);
                    else ArcQuestNetwork.syncTrackedQuest(player, data);
                }
            }
        });
        context.setPacketHandled(true);
    }

    public static void clearPlayer(UUID playerId) {
        TrackedPhaseFocusService.clearPlayer(playerId);
    }

    public static void clear() {
        TrackedPhaseFocusService.clearAll();
    }
}
