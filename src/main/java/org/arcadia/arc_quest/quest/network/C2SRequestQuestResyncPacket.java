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
import org.arcadia.arc_quest.questplayer.PlayerSessionEpochManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class C2SRequestQuestResyncPacket implements CustomPacketPayload {

    public static final Type<C2SRequestQuestResyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "request_quest_resync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRequestQuestResyncPacket> STREAM_CODEC =
            StreamCodec.ofMember(C2SRequestQuestResyncPacket::encode, C2SRequestQuestResyncPacket::decode);

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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SRequestQuestResyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !PlayerSessionEpochManager.matches(player, packet.playerSessionEpoch)) return;
            long currentTick = player.server.getTickCount();
            Long previousTick = LAST_REQUEST_TICKS.get(player.getUUID());
            if (previousTick != null && currentTick - previousTick < MIN_REQUEST_INTERVAL_TICKS) return;
            LAST_REQUEST_TICKS.put(player.getUUID(), currentTick);
            ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
            if (data != null) ArcQuestNetwork.syncFullData(player, data);
        });
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
