package org.arcadia.arc_quest.integration.jei.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;

import org.arcadia.arc_quest.client.compat.jei.JeiCatalogClient;


public record S2CJeiCatalogChunk(long nonce, long revision, long epoch, int index, int count,
                                 int totalBytes, byte[] digest, byte[] payload) implements CustomPacketPayload {
    public static final Type<S2CJeiCatalogChunk> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "jei_catalog_chunk"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CJeiCatalogChunk> STREAM_CODEC = StreamCodec.ofMember(S2CJeiCatalogChunk::encode, S2CJeiCatalogChunk::decode);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(S2CJeiCatalogChunk packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.nonce); buffer.writeLong(packet.revision); buffer.writeLong(packet.epoch);
        buffer.writeVarInt(packet.index); buffer.writeVarInt(packet.count); buffer.writeVarInt(packet.totalBytes);
        buffer.writeByteArray(packet.digest); buffer.writeByteArray(packet.payload);
    }
    public static S2CJeiCatalogChunk decode(FriendlyByteBuf buffer) {
        return new S2CJeiCatalogChunk(buffer.readLong(), buffer.readLong(), buffer.readLong(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readByteArray(32), buffer.readByteArray(JeiCatalogCodec.CHUNK_BYTES));
    }
    public static void handle(S2CJeiCatalogChunk packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientOnly.accept(packet));
    }
    private static final class ClientOnly {
        private static void accept(S2CJeiCatalogChunk packet) {
            JeiCatalogClient.accept(packet);
        }
    }
}
