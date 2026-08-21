package org.arcadia.arc_quest.data.sync.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.data.sync.DatapackContentSyncService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public record C2SRequestDatapackContentPacket(long clientEpoch) implements CustomPacketPayload {
    public static final Type<C2SRequestDatapackContentPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "request_datapack_content"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRequestDatapackContentPacket> STREAM_CODEC =
            StreamCodec.ofMember(C2SRequestDatapackContentPacket::encode, C2SRequestDatapackContentPacket::decode);
    private static final long REQUEST_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(1L);
    private static final Map<UUID, Long> LAST_REQUEST = new ConcurrentHashMap<>();

    public static void encode(C2SRequestDatapackContentPacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.clientEpoch);
    }

    public static C2SRequestDatapackContentPacket decode(FriendlyByteBuf buffer) {
        return new C2SRequestDatapackContentPacket(buffer.readLong());
    }

    public static void handle(C2SRequestDatapackContentPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                long now = System.nanoTime();
                Long previous = LAST_REQUEST.get(player.getUUID());
                if (previous != null && now - previous < REQUEST_INTERVAL_NANOS) return;
                LAST_REQUEST.put(player.getUUID(), now);
                DatapackContentSyncService.sendToPlayer(player);
            }
        });
    }

    public static void clearPlayer(UUID playerId) {
        LAST_REQUEST.remove(playerId);
    }

    public static void clear() {
        LAST_REQUEST.clear();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
