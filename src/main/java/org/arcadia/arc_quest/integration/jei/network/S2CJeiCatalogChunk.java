package org.arcadia.arc_quest.integration.jei.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record S2CJeiCatalogChunk(long nonce, long revision, long epoch, int index, int count,
                                 int totalBytes, byte[] digest, byte[] payload) {
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
    public static void handle(S2CJeiCatalogChunk packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientOnly.accept(packet)));
        context.setPacketHandled(true);
    }
    private static final class ClientOnly {
        private static void accept(S2CJeiCatalogChunk packet) {
            org.arcadia.arc_quest.client.compat.jei.JeiCatalogClient.accept(packet);
        }
    }
}
