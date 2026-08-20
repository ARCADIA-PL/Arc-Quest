package org.arcadia.arc_quest.data.sync.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.data.sync.DatapackContentSyncService;

import java.util.Arrays;
import java.util.function.Supplier;

public record S2CDatapackContentChunkPacket(long epoch, String contentHash, int chunkIndex, byte[] payload) {
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

    public static void handle(S2CDatapackContentChunkPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DatapackContentClientNetworkBridge.handleChunk(packet));
        context.setPacketHandled(true);
    }
}
