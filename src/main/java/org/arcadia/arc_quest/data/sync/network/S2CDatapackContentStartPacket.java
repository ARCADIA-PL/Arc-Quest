package org.arcadia.arc_quest.data.sync.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record S2CDatapackContentStartPacket(long epoch, String contentHash, int chunkCount,
                                            int compressedBytes, int uncompressedBytes) {
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

    public static void handle(S2CDatapackContentStartPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DatapackContentClientNetworkBridge.handleStart(packet));
        context.setPacketHandled(true);
    }
}
