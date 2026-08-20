package org.arcadia.arc_quest.data.sync.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;

public record S2CDatapackContentStartPacket(long epoch, String contentHash, int chunkCount,
                                            int compressedBytes, int uncompressedBytes) implements CustomPacketPayload {
    public static final Type<S2CDatapackContentStartPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, datapack_content_start));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CDatapackContentStartPacket> STREAM_CODEC =
            StreamCodec.ofMember(S2CDatapackContentStartPacket::encode, S2CDatapackContentStartPacket::decode);
    public static void encode(S2CDatapackContentStartPacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.epoch);
        buffer.writeUtf(packet.contentHash, 64);
        buffer.writeVarInt(packet.chunkCount);
        buffer.writeVarInt(packet.compressedBytes);
        buffer.writeVarInt(packet.uncompressedBytes);
    }

    public static S2CDatapackContentStartPacket decode(FriendlyByteBuf buffer) {
        return new S2CDatapackContentStartPacket(buffer.readLong(), buffer.readUtf(64), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(S2CDatapackContentStartPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> DatapackContentClientNetworkBridge.handleStart(packet));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
