package org.arcadia.arc_quest.data.sync.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.data.sync.DatapackContentSyncService;

import java.util.Arrays;

public record S2CDatapackContentChunkPacket(long epoch, String contentHash, int chunkIndex,
                                            byte[] payload) implements CustomPacketPayload {
    public static final Type<S2CDatapackContentChunkPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, datapack_content_chunk));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CDatapackContentChunkPacket> STREAM_CODEC =
            StreamCodec.ofMember(S2CDatapackContentChunkPacket::encode, S2CDatapackContentChunkPacket::decode);
    public S2CDatapackContentChunkPacket {
        payload = Arrays.copyOf(payload, payload.length);
    }

    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }

    public static void encode(S2CDatapackContentChunkPacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.epoch);
        buffer.writeUtf(packet.contentHash, 64);
        buffer.writeVarInt(packet.chunkIndex);
        buffer.writeByteArray(packet.payload);
    }

    public static S2CDatapackContentChunkPacket decode(FriendlyByteBuf buffer) {
        return new S2CDatapackContentChunkPacket(buffer.readLong(), buffer.readUtf(64), buffer.readVarInt(),
                buffer.readByteArray(DatapackContentSyncService.CHUNK_BYTES));
    }

    public static void handle(S2CDatapackContentChunkPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> DatapackContentClientNetworkBridge.handleChunk(packet));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
