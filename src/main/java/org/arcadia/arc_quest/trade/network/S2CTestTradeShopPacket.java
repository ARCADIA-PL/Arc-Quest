package org.arcadia.arc_quest.trade.network;

import net.minecraft.network.FriendlyByteBuf;
import org.arcadia.arc_quest.trade.demo.RefreshingTradeCatalog;
import java.util.ArrayList;

/** 测试专用、最多 12 个条目的小快照，不能指定任意物品或任意商店 ID。 */
public record S2CTestTradeShopPacket(RefreshingTradeCatalog catalog) {
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
}
