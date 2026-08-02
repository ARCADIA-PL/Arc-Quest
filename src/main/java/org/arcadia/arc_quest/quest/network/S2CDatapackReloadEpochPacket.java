package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record S2CDatapackReloadEpochPacket(long epoch) {
    public static void encode(S2CDatapackReloadEpochPacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.epoch);
    }

    public static S2CDatapackReloadEpochPacket decode(FriendlyByteBuf buffer) {
        return new S2CDatapackReloadEpochPacket(buffer.readLong());
    }

    public static void handle(S2CDatapackReloadEpochPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientQuestCache.INSTANCE.setDatapackReloadEpoch(packet.epoch));
        context.setPacketHandled(true);
    }
}
