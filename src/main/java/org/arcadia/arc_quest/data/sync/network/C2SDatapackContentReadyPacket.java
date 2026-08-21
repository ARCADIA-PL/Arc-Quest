package org.arcadia.arc_quest.data.sync.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.data.reload.ArcQuestReloadCoordinator;
import org.arcadia.arc_quest.data.sync.DatapackContentSyncService;
import org.arcadia.arc_quest.guide.runtime.GuidePlayerStateSyncService;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public record C2SDatapackContentReadyPacket(long epoch) implements CustomPacketPayload {
    public static final Type<C2SDatapackContentReadyPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "datapack_content_ready"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SDatapackContentReadyPacket> STREAM_CODEC =
            StreamCodec.ofMember(C2SDatapackContentReadyPacket::encode, C2SDatapackContentReadyPacket::decode);
    private static final Map<UUID, Long> ACKNOWLEDGED_EPOCHS = new ConcurrentHashMap<>();

    public static void encode(C2SDatapackContentReadyPacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.epoch);
    }

    public static C2SDatapackContentReadyPacket decode(FriendlyByteBuf buffer) {
        return new C2SDatapackContentReadyPacket(buffer.readLong());
    }

    public static void handle(C2SDatapackContentReadyPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) acknowledge(player, packet.epoch);
        });
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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
