package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.questplayer.PlayerSessionEpochManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class C2SRequestQuestResyncPacket {

    private static final long MIN_REQUEST_INTERVAL_TICKS = 20L;
    private static final Map<UUID, Long> LAST_REQUEST_TICKS = new ConcurrentHashMap<>();

    private final long playerSessionEpoch;
    private final long clientRevision;

    public C2SRequestQuestResyncPacket(long playerSessionEpoch, long clientRevision) {
        this.playerSessionEpoch = Math.max(0L, playerSessionEpoch);
        this.clientRevision = Math.max(-1L, clientRevision);
    }

    public static void encode(C2SRequestQuestResyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.playerSessionEpoch);
        buffer.writeLong(packet.clientRevision);
    }

    public static C2SRequestQuestResyncPacket decode(FriendlyByteBuf buffer) {
        return new C2SRequestQuestResyncPacket(buffer.readLong(), buffer.readLong());
    }

    public static void handle(C2SRequestQuestResyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !PlayerSessionEpochManager.matches(player, packet.playerSessionEpoch)) return;
            long currentTick = player.server.getTickCount();
            Long previousTick = LAST_REQUEST_TICKS.get(player.getUUID());
            if (previousTick != null && currentTick - previousTick < MIN_REQUEST_INTERVAL_TICKS) return;
            LAST_REQUEST_TICKS.put(player.getUUID(), currentTick);
            ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
            if (data != null) ArcQuestNetwork.syncFullData(player, data);
        });
        context.setPacketHandled(true);
    }

    public static void clearPlayer(UUID playerId) {
        LAST_REQUEST_TICKS.remove(playerId);
    }

    public static void clear() {
        LAST_REQUEST_TICKS.clear();
    }

    public long getPlayerSessionEpoch() {
        return playerSessionEpoch;
    }

    public long getClientRevision() {
        return clientRevision;
    }
}
