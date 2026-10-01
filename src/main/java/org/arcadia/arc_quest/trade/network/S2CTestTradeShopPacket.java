package org.arcadia.arc_quest.trade.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;

import org.arcadia.arc_quest.trade.demo.RefreshingTradeCatalog;
import java.util.ArrayList;

/** 测试专用、最多 12 个条目的小快照，不能指定任意物品或任意商店 ID。 */
public record S2CTestTradeShopPacket(RefreshingTradeCatalog catalog) implements CustomPacketPayload {
    public static final Type<S2CTestTradeShopPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "test_trade_shop"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CTestTradeShopPacket> STREAM_CODEC = StreamCodec.ofMember(S2CTestTradeShopPacket::encode, S2CTestTradeShopPacket::decode);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(S2CTestTradeShopPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarLong(packet.catalog.round());
        buffer.writeVarInt(packet.catalog.entries().size());
        for (var listing : packet.catalog.entries()) {
            buffer.writeVarInt(listing.slot());
            buffer.writeVarInt(listing.product());
            buffer.writeVarInt(listing.price());
            buffer.writeVarInt(listing.count());
        }
    }

    public static S2CTestTradeShopPacket decode(FriendlyByteBuf buffer) {
        long round = buffer.readVarLong();
        int count = buffer.readVarInt();
        if (count < 0 || count > RefreshingTradeCatalog.MAX_ENTRIES) throw new IllegalArgumentException("Invalid test shop size");
        var listings = new ArrayList<RefreshingTradeCatalog.Listing>(count);
        for (int i = 0; i < count; i++) {
            listings.add(new RefreshingTradeCatalog.Listing(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()));
        }
        return new S2CTestTradeShopPacket(new RefreshingTradeCatalog(round, listings));
    }
    public static void handle(S2CTestTradeShopPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientOnly.accept(packet));
    }
    private static final class ClientOnly {
        private static void accept(S2CTestTradeShopPacket packet) {
            org.arcadia.arc_quest.client.hud.shop.ClientRefreshingTestShop.accept(packet);
        }
    }
}
