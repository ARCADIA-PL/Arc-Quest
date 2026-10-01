package org.arcadia.arc_quest.trade.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;


public record C2SReadTradeUpdatePacket(String shopId, String entryId, long revision, long epoch) implements CustomPacketPayload {
    public static final Type<C2SReadTradeUpdatePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "read_trade_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SReadTradeUpdatePacket> STREAM_CODEC = StreamCodec.ofMember(C2SReadTradeUpdatePacket::encode, C2SReadTradeUpdatePacket::decode);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(C2SReadTradeUpdatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.shopId, 256);
        buffer.writeUtf(packet.entryId, 256);
        buffer.writeLong(packet.revision);
        buffer.writeLong(packet.epoch);
    }

    public static C2SReadTradeUpdatePacket decode(FriendlyByteBuf buffer) {
        return new C2SReadTradeUpdatePacket(buffer.readUtf(256), buffer.readUtf(256), buffer.readLong(), buffer.readLong());
    }

    public static void handle(C2SReadTradeUpdatePacket packet, IPayloadContext context) {
        if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
            context.enqueueWork(() -> TradeScreenService.readUpdate(player, packet));
        }
    }
}
