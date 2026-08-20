package org.arcadia.arc_quest.data.sync.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.data.reload.ArcQuestReloadCoordinator;
import org.arcadia.arc_quest.data.sync.DatapackContentSyncService;
import org.arcadia.arc_quest.guide.runtime.GuidePlayerStateSyncService;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public record C2SDatapackContentReadyPacket(long epoch) {
    private static final Map<UUID, Long> ACKNOWLEDGED_EPOCHS = new ConcurrentHashMap<>();

    public static void encode(C2SDatapackContentReadyPacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.epoch);
    }

    public static C2SDatapackContentReadyPacket decode(FriendlyByteBuf buffer) {
        return new C2SDatapackContentReadyPacket(buffer.readLong());
    }

    public static void handle(C2SDatapackContentReadyPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) context.enqueueWork(() -> acknowledge(player, packet.epoch));
        context.setPacketHandled(true);
    }

    private static void acknowledge(ServerPlayer player, long epoch) {
        long serverEpoch = ArcQuestReloadCoordinator.INSTANCE.getCommittedEpoch();
        if (epoch != serverEpoch) {
            DatapackContentSyncService.sendToPlayer(player);
            return;
        }
        Long previous = ACKNOWLEDGED_EPOCHS.put(player.getUUID(), epoch);
        if (previous != null && previous == epoch) return;
        ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
        ArcQuestNetwork.syncFullData(player, data);
        ArcQuestNetwork.sendDatapackReloadEpoch(player, epoch);
        GuidePlayerStateSyncService.sync(player, data);
    }

    public static void clearPlayer(UUID playerId) {
        ACKNOWLEDGED_EPOCHS.remove(playerId);
    }

    public static void clear() {
        ACKNOWLEDGED_EPOCHS.clear();
    }
}
