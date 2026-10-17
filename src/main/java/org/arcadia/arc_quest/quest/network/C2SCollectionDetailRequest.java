package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.UUID;

/** The client supplies a real collection run, never an arbitrary shop or provider identifier. */
public record C2SCollectionDetailRequest(UUID requestId, long playerEpoch, String questId,
                                        String phaseId, String bindingId, String runId) implements CustomPacketPayload {
    public static final Type<C2SCollectionDetailRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "collection_detail_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SCollectionDetailRequest> STREAM_CODEC =
            StreamCodec.ofMember(C2SCollectionDetailRequest::encode, C2SCollectionDetailRequest::decode);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
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
    public static void handle(C2SCollectionDetailRequest packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !CollectionDetailService.allowRequest(player)) return;
            PacketDistributor.sendToPlayer(player,
                    new S2CCollectionDetailPacket(packet, CollectionDetailService.resolve(player, packet)));
        });
    }
}
