package org.arcadia.arc_quest.trade.demo;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.arcadia.arc_quest.trade.network.S2CTestTradeShopPacket;
import org.arcadia.arc_quest.trade.runtime.TradeUpdateStore;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class RefreshingTradeCatalogTest {
    @Test
    void startsWithSixProductsAcrossAllCategories() {
        var catalog = RefreshingTradeCatalog.initial(new Random(42));
        assertEquals(6, catalog.entries().size());
        assertEquals(3, catalog.entries().stream().map(entry -> entry.product() / 4).distinct().count());
        assertEquals(0, catalog.round());
    }

    @Test
    void everyRefreshAddsAndReplacesWithoutUnboundedGrowth() {
        var random = new Random(88);
        var catalog = RefreshingTradeCatalog.initial(random);
        for (int round = 1; round <= 1000; round++) {
            Map<Integer, RefreshingTradeCatalog.Listing> previous = catalog.entries().stream()
                    .collect(Collectors.toMap(RefreshingTradeCatalog.Listing::slot, entry -> entry));
            var next = catalog.next(random);
            assertEquals(round, next.round());
            assertEquals(Math.min(12, 6 + round), next.entries().size());
            assertEquals(1, next.entries().stream().filter(entry -> !previous.containsKey(entry.slot())).count());
            assertEquals(1, next.entries().stream().filter(entry -> previous.containsKey(entry.slot())
                    && previous.get(entry.slot()).product() != entry.product()).count());
            assertTrue(next.entries().stream().allMatch(entry -> entry.slot() < RefreshingTradeCatalog.SLOT_COUNT));
            catalog = next;
        }
    }

    @Test
    void refreshActuallyTriggersNewArrivalAndRewardHighlights() {
        var random = new Random(7);
        var catalog = RefreshingTradeCatalog.initial(random);
        var store = new TradeUpdateStore();
        store.observe("test", snapshots(catalog));
        assertTrue(store.pending("test").isEmpty());
        for (int round = 0; round < 40; round++) {
            catalog = catalog.next(random);
            store.observe("test", snapshots(catalog));
            var notices = store.pending("test");
            assertEquals(1, notices.values().stream().filter(notice -> (notice.reasons() & TradeUpdateStore.ADDED) != 0).count());
            assertEquals(1, notices.values().stream().filter(notice -> (notice.reasons() & TradeUpdateStore.REWARD) != 0).count());
            notices.forEach((id, notice) -> assertTrue(store.acknowledge("test", id, notice.revision())));
        }
    }

    @Test
    void snapshotRoundTripsAndIsSmall() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var packet = new S2CTestTradeShopPacket(RefreshingTradeCatalog.initial(new Random(9)));
            S2CTestTradeShopPacket.encode(packet, buffer);
            assertTrue(buffer.readableBytes() < 128);
            assertEquals(packet, S2CTestTradeShopPacket.decode(buffer));
            buffer.clear();
            var stop = new S2CTestTradeShopPacket(new RefreshingTradeCatalog(0, List.of()));
            S2CTestTradeShopPacket.encode(stop, buffer);
            assertEquals(stop, S2CTestTradeShopPacket.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test
    void rejectsOversizedPacketsAndInvalidOrDuplicateSlots() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarLong(1);
            buffer.writeVarInt(13);
            assertThrows(IllegalArgumentException.class, () -> S2CTestTradeShopPacket.decode(buffer));
        } finally { buffer.release(); }
        assertThrows(IllegalArgumentException.class, () -> new RefreshingTradeCatalog.Listing(24, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new RefreshingTradeCatalog.Listing(0, 12, 1, 1));
        var entry = new RefreshingTradeCatalog.Listing(0, 0, 1, 1);
        assertThrows(IllegalArgumentException.class, () -> new RefreshingTradeCatalog(0, List.of(entry, entry)));
    }

    private static Map<String, TradeUpdateStore.Snapshot> snapshots(RefreshingTradeCatalog catalog) {
        Map<String, TradeUpdateStore.Snapshot> result = new LinkedHashMap<>();
        catalog.entries().forEach(entry -> result.put("slot_" + entry.slot(), new TradeUpdateStore.Snapshot(
                Integer.toString(entry.price()), entry.product() + ":" + entry.count(), true, true, false)));
        return result;
    }
}
