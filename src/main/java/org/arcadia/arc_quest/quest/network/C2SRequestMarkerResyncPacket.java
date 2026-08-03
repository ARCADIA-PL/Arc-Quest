package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

import java.util.function.Supplier;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class C2SRequestMarkerResyncPacket {
    private static final Map<UUID, Integer> LAST_REQUEST_TICK = new ConcurrentHashMap<>();
    public static void encode(C2SRequestMarkerResyncPacket packet, FriendlyByteBuf buffer) {
    }

    public static C2SRequestMarkerResyncPacket decode(FriendlyByteBuf buffer) {
        return new C2SRequestMarkerResyncPacket();
    }

    public static void handle(C2SRequestMarkerResyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            Integer lastTick = LAST_REQUEST_TICK.put(player.getUUID(), player.tickCount);
            if (lastTick != null && player.tickCount - lastTick < 20) return;
            ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
            if (data != null) ArcQuestNetwork.syncMarkers(player, data);
        });
        context.setPacketHandled(true);
    }

    public static void clearPlayer(UUID playerId) {
        LAST_REQUEST_TICK.remove(playerId);
    }
}
