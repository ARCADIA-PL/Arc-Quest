package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class C2SRequestMarkerResyncPacket implements CustomPacketPayload {
    public static final Type<C2SRequestMarkerResyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "request_marker_resync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRequestMarkerResyncPacket> STREAM_CODEC =
            StreamCodec.ofMember(C2SRequestMarkerResyncPacket::encode, C2SRequestMarkerResyncPacket::decode);

    private static final long MIN_REQUEST_INTERVAL_TICKS = 20L;
    private static final Map<UUID, Long> LAST_REQUEST_TICKS = new ConcurrentHashMap<>();

    public static void encode(C2SRequestMarkerResyncPacket packet, FriendlyByteBuf buffer) {
    }

    public static C2SRequestMarkerResyncPacket decode(FriendlyByteBuf buffer) {
        return new C2SRequestMarkerResyncPacket();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SRequestMarkerResyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            long currentTick = player.server.getTickCount();
            Long previousTick = LAST_REQUEST_TICKS.get(player.getUUID());
            if (previousTick != null && currentTick - previousTick < MIN_REQUEST_INTERVAL_TICKS) return;
            LAST_REQUEST_TICKS.put(player.getUUID(), currentTick);
            ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
            if (data != null) ArcQuestNetwork.syncMarkers(player, data);
        });
    }

    public static void clearPlayer(UUID playerId) {
        LAST_REQUEST_TICKS.remove(playerId);
    }
}
