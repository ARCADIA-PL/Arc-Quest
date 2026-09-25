package org.arcadia.arc_quest.trade.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record C2SReadTradeUpdatePacket(String shopId, String entryId, long revision, long epoch) {
    public static void encode(C2SReadTradeUpdatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.shopId, 256);
        buffer.writeUtf(packet.entryId, 256);
        buffer.writeLong(packet.revision);
        buffer.writeLong(packet.epoch);
    }

    public static C2SReadTradeUpdatePacket decode(FriendlyByteBuf buffer) {
        return new C2SReadTradeUpdatePacket(buffer.readUtf(256), buffer.readUtf(256), buffer.readLong(), buffer.readLong());
    }

    public static void handle(C2SReadTradeUpdatePacket packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> TradeScreenService.readUpdate(context.getSender(), packet));
        context.setPacketHandled(true);
    }
}
