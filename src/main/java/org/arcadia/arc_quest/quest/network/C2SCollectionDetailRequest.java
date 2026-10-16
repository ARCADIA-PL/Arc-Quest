package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import java.util.UUID;
import java.util.function.Supplier;

/** The client supplies a real collection run, never an arbitrary shop or provider identifier. */
public record C2SCollectionDetailRequest(UUID requestId, long playerEpoch, String questId,
                                        String phaseId, String bindingId, String runId) {
    public C2SCollectionDetailRequest {
        java.util.Objects.requireNonNull(requestId);
        for (var value : java.util.List.of(questId, phaseId, bindingId, runId))
            if (value.isEmpty() || value.length() > 256) throw new IllegalArgumentException("Invalid collection detail context");
    }
    public static void encode(C2SCollectionDetailRequest packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.requestId); buffer.writeLong(packet.playerEpoch);
        buffer.writeUtf(packet.questId, 256); buffer.writeUtf(packet.phaseId, 256);
        buffer.writeUtf(packet.bindingId, 256); buffer.writeUtf(packet.runId, 256);
    }
    public static C2SCollectionDetailRequest decode(FriendlyByteBuf buffer) {
        return new C2SCollectionDetailRequest(buffer.readUUID(), buffer.readLong(), buffer.readUtf(256),
                buffer.readUtf(256), buffer.readUtf(256), buffer.readUtf(256));
    }
    public static void handle(C2SCollectionDetailRequest packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            var player = context.get().getSender();
            if (player == null || !CollectionDetailService.allowRequest(player)) return;
            ArcQuestNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new S2CCollectionDetailPacket(packet, CollectionDetailService.resolve(player, packet)));
        });
        context.get().setPacketHandled(true);
    }
}
