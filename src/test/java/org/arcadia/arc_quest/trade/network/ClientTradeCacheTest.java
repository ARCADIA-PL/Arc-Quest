package org.arcadia.arc_quest.trade.network;

import org.arcadia.arc_quest.trade.api.TradeShopDefinition;
import org.arcadia.arc_quest.trade.builder.TradeEntryBuilder;
import org.arcadia.arc_quest.trade.builder.TradeShopBuilder;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientTradeCacheTest {
    private static final String SHOP = "test:index";
    private final ClientTradeCache cache = ClientTradeCache.INSTANCE;

    @BeforeEach @AfterEach void clear() {
        cache.clear();
        TradeRegistry.clearAll();
    }

    @Test void indexesAllEntriesInDefinitionOrderAndRejectsMissingIds() {
        var builder = TradeShopBuilder.create(SHOP);
        for (int i = 0; i < 600; i++) builder.entry(entry("entry_" + i));
        TradeRegistry.registerDatapack(builder.build());
        assertEquals(-1, cache.getGlobalIndex(SHOP, "missing"));
        for (int i = 599; i >= 0; i--) assertEquals(i, cache.getGlobalIndex(SHOP, "entry_" + i));
        assertEquals(-1, cache.getGlobalIndex(SHOP, null));
        assertEquals(-1, cache.getGlobalIndex(SHOP, ""));
        assertEquals(-1, cache.getGlobalIndex("test:unknown", "entry_0"));
    }

    @Test void definitionReplacementAndRemovalCannotReuseStaleIndices() {
        TradeRegistry.registerDatapack(shop("a", "b"));
        assertEquals(1, cache.getGlobalIndex(SHOP, "b"));
        TradeRegistry.replaceDatapackSnapshot(Map.of(SHOP, shop("b", "c", "a")));
        assertEquals(0, cache.getGlobalIndex(SHOP, "b"));
        assertEquals(2, cache.getGlobalIndex(SHOP, "a"));
        assertEquals(1, cache.getGlobalIndex(SHOP, "c"));
        cache.invalidateDefinition(SHOP);
        assertEquals(2, cache.getGlobalIndex(SHOP, "a"));
        TradeRegistry.replaceDatapackSnapshot(Map.of());
        assertEquals(-1, cache.getGlobalIndex(SHOP, "b"));
    }

    @Test void temporaryPresentationReplacementAndClearFollowTheirOwnOrder() {
        cache.setPresentation(shop("a", "b"));
        assertEquals(1, cache.getGlobalIndex(SHOP, "b"));
        cache.setPresentation(shop("b", "a"));
        assertEquals(0, cache.getGlobalIndex(SHOP, "b"));
        assertEquals(1, cache.getGlobalIndex(SHOP, "a"));
        cache.clearPresentation(SHOP);
        assertEquals(-1, cache.getGlobalIndex(SHOP, "a"));
    }

    @Test void authorityRefreshRetainsCorrectEntryToStateAlignment() {
        TradeRegistry.registerDatapack(shop("a", "b"));
        assertEquals(1, cache.getGlobalIndex(SHOP, "b"));
        cache.updateSession(SHOP, new int[]{2, 7}, new int[]{5, 9}, new long[2], new long[2],
                new long[2], new int[2], new long[2], new int[2], new boolean[]{true, true}, new boolean[]{true, true});
        assertEquals(7, cache.getPurchaseCount(SHOP, cache.getGlobalIndex(SHOP, "b")));
        assertEquals(2, cache.getRemainingPurchases(SHOP, cache.getGlobalIndex(SHOP, "b")));
        assertEquals(2, cache.getPurchaseCount(SHOP, cache.getGlobalIndex(SHOP, "a")));
    }

    private static TradeShopDefinition shop(String... ids) {
        var builder = TradeShopBuilder.create(SHOP);
        for (var id : ids) builder.entry(entry(id));
        return builder.build();
    }

    private static TradeEntryBuilder entry(String id) {
        return TradeEntryBuilder.create(id).rewardFlag("test_reward");
    }
}
