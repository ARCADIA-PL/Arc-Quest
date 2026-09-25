package org.arcadia.arc_quest.trade.network;

import net.minecraft.network.FriendlyByteBuf;
import org.arcadia.arc_quest.trade.runtime.TradeUpdateStore;

import java.util.LinkedHashMap;
import java.util.Map;

/** 仅发送可见商品的更新摘要，不携带价格签名或隐藏剧情内容。 */
public record S2CTradeUpdatesPacket(String shopId, long epoch, Map<String, Entry> entries) {
    public S2CTradeUpdatesPacket { entries = Map.copyOf(entries); }
    public record Entry(String category, int reasons, long revision) { }

    public static void encode(S2CTradeUpdatesPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.shopId, 256);
        buffer.writeLong(packet.epoch);
        buffer.writeVarInt(packet.entries.size());
        packet.entries.forEach((id, entry) -> {
            buffer.writeUtf(id, 256);
            buffer.writeUtf(entry.category(), 256);
            buffer.writeVarInt(entry.reasons());
            buffer.writeLong(entry.revision());
        });
    }

    public static S2CTradeUpdatesPacket decode(FriendlyByteBuf buffer) {
        String shop = buffer.readUtf(256);
        long epoch = buffer.readLong();
        int count = buffer.readVarInt();
        if (count < 0 || count > TradeUpdateStore.MAX_ENTRIES) throw new IllegalArgumentException("Invalid trade update count");
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String id = buffer.readUtf(256), category = buffer.readUtf(256);
            int reasons = buffer.readVarInt();
            long revision = buffer.readLong();
            if (!TradeUpdateStore.validId(id) || reasons <= 0 || (reasons & ~TradeUpdateStore.ALL_REASONS) != 0 || revision <= 0
                    || entries.put(id, new Entry(category, reasons, revision)) != null) throw new IllegalArgumentException("Invalid trade update entry");
        }
        if (!TradeUpdateStore.validId(shop) || epoch <= 0) throw new IllegalArgumentException("Invalid trade update envelope");
        return new S2CTradeUpdatesPacket(shop, epoch, entries);
    }
}
