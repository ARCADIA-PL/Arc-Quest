package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.data.sync.network.DatapackContentClientNetworkBridge;

public record S2CDatapackReloadEpochPacket(long epoch) implements CustomPacketPayload {
    public static final Type<S2CDatapackReloadEpochPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "datapack_reload_epoch"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CDatapackReloadEpochPacket> STREAM_CODEC =
            StreamCodec.ofMember(S2CDatapackReloadEpochPacket::encode, S2CDatapackReloadEpochPacket::decode);

    public static void encode(S2CDatapackReloadEpochPacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.epoch);
    }

    public static S2CDatapackReloadEpochPacket decode(FriendlyByteBuf buffer) {
        return new S2CDatapackReloadEpochPacket(buffer.readLong());
    }

    public static void handle(S2CDatapackReloadEpochPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> DatapackContentClientNetworkBridge.handleEpoch(packet.epoch));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
