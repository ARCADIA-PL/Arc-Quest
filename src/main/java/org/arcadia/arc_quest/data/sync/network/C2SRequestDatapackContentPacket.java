package org.arcadia.arc_quest.data.sync.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.data.sync.DatapackContentSyncService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public record C2SRequestDatapackContentPacket(long clientEpoch) {
    private static final long REQUEST_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(1L);
    private static final Map<UUID, Long> LAST_REQUEST = new ConcurrentHashMap<>();

    public static void encode(C2SRequestDatapackContentPacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.clientEpoch);
    }

    public static C2SRequestDatapackContentPacket decode(FriendlyByteBuf buffer) {
        return new C2SRequestDatapackContentPacket(buffer.readLong());
    }

    public static void handle(C2SRequestDatapackContentPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            context.enqueueWork(() -> {
                long now = System.nanoTime();
                Long previous = LAST_REQUEST.get(player.getUUID());
                if (previous != null && now - previous < REQUEST_INTERVAL_NANOS) return;
                LAST_REQUEST.put(player.getUUID(), now);
                DatapackContentSyncService.sendToPlayer(player);
            });
        }
        context.setPacketHandled(true);
    }

    public static void clearPlayer(UUID playerId) {
        LAST_REQUEST.remove(playerId);
    }

    public static void clear() {
        LAST_REQUEST.clear();
    }
}
