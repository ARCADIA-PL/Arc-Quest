package org.arcadia.arc_quest.trade.runtime;

import org.arcadia.arc_quest.quest.data.TradeDataStore;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.arcadia.arc_quest.trade.runtime.TradeUpdateStore.*;

class TradeUpdateStoreTest {
    private static Snapshot snapshot(boolean visible, boolean unlocked, boolean soldOut) {
        return new Snapshot("a".repeat(64), "b".repeat(64), visible, unlocked, soldOut);
    }

    @Test
    void firstVisitAndOrdinaryPurchasesDoNotHighlight() {
        var store = new TradeUpdateStore();
        store.observe("shop", Map.of("item", snapshot(true, true, false)));
        assertTrue(store.pending("shop").isEmpty());
        store.clearDirty();
        store.observe("shop", Map.of("item", snapshot(true, true, false)));
        assertFalse(store.isDirty());
        store.observe("shop", Map.of("item", snapshot(true, true, true)));
        assertTrue(store.pending("shop").isEmpty());
    }

    @Test
    void restockRequiresPreviouslySoldOut() {
        var store = new TradeUpdateStore();
        store.observe("shop", Map.of("item", snapshot(true, true, true)));
        store.observe("shop", Map.of("item", snapshot(true, true, false)));
        assertEquals(RESTOCKED, store.pending("shop").get("item").reasons());
    }

    @Test
    void addedAndRevealedEntriesAreDistinct() {
        var store = new TradeUpdateStore();
        store.observe("shop", Map.of("hidden", snapshot(false, false, false)));
        store.observe("shop", Map.of("hidden", snapshot(true, true, false), "new", snapshot(true, true, false)));
        assertEquals(UNLOCKED, store.pending("shop").get("hidden").reasons());
        assertEquals(ADDED, store.pending("shop").get("new").reasons());
    }

    @Test
    void purchaseConditionUnlockIsIndependentOfStock() {
        var store = new TradeUpdateStore();
        store.observe("shop", Map.of("item", snapshot(true, false, false)));
        store.observe("shop", Map.of("item", snapshot(true, true, false)));
        assertEquals(UNLOCKED, store.pending("shop").get("item").reasons());
    }

    @Test
    void priceAndRewardChangesMergeAndOldAcknowledgementCannotClearThem() {
        var store = new TradeUpdateStore();
        store.observe("shop", Map.of("item", snapshot(true, true, false)));
        store.observe("shop", Map.of("item", new Snapshot("c".repeat(64), "b".repeat(64), true, true, false)));
        var first = store.pending("shop").get("item");
        assertEquals(PRICE, first.reasons());
        store.observe("shop", Map.of("item", new Snapshot("c".repeat(64), "d".repeat(64), true, true, false)));
        var second = store.pending("shop").get("item");
        assertEquals(PRICE | REWARD, second.reasons());
        assertFalse(store.acknowledge("shop", "item", first.revision()));
        assertTrue(store.acknowledge("shop", "item", second.revision()));
        assertTrue(store.pending("shop").isEmpty());
    }

    @Test
    void hiddenChangesAreRetainedWithoutLeakingAndRemovalClearsPending() {
        var store = new TradeUpdateStore();
        store.observe("shop", Map.of("item", snapshot(false, false, false)));
        var changed = new Snapshot("c".repeat(64), "b".repeat(64), false, false, false);
        store.observe("shop", Map.of("item", changed));
        assertTrue(store.pending("shop").isEmpty());
        assertFalse(store.acknowledge("shop", "item", 1));
        store.observe("shop", Map.of("item", new Snapshot(changed.price(), changed.reward(), true, true, false)));
        assertEquals(PRICE | UNLOCKED, store.pending("shop").get("item").reasons());
        store.observe("shop", Map.of());
        assertTrue(store.pending("shop").isEmpty());
    }

    @Test
    void persistenceCopyAndDirtyTrackingIncludeUnreadState() {
        var data = new TradeDataStore();
        data.getUpdates().observe("shop", Map.of());
        data.getUpdates().observe("shop", Map.of("item", snapshot(true, true, false)));
        assertTrue(data.isDirty());
        data.clearDirty();
        assertFalse(data.isDirty());
        var loaded = new TradeDataStore();
        loaded.deserialize(data.serialize());
        var copy = new TradeDataStore();
        copy.copyFrom(loaded);
        assertEquals(data.getUpdates().pending("shop"), copy.getUpdates().pending("shop"));
        long revision = copy.getUpdates().pending("shop").get("item").revision();
        assertTrue(copy.getUpdates().acknowledge("shop", "item", revision));
        assertTrue(copy.isDirty());
        assertFalse(loaded.getUpdates().pending("shop").isEmpty());
        loaded.deserialize(copy.serialize());
        assertTrue(loaded.getUpdates().pending("shop").isEmpty());
        copy.clear();
        assertTrue(copy.getUpdates().pending("shop").isEmpty());
    }

    @Test
    void shopsAndPlayersHaveIndependentBaselines() {
        var one = new TradeUpdateStore();
        var two = new TradeUpdateStore();
        one.observe("shop", Map.of());
        one.observe("shop", Map.of("item", snapshot(true, true, false)));
        one.observe("other", Map.of("item", snapshot(true, true, false)));
        two.observe("shop", Map.of("item", snapshot(true, true, false)));
        assertFalse(one.pending("shop").isEmpty());
        assertTrue(one.pending("other").isEmpty());
        assertTrue(two.pending("shop").isEmpty());
    }

    @Test
    void removedThenReaddedEntryRejectsOldRevision() {
        var store = new TradeUpdateStore();
        store.observe("shop", Map.of());
        store.observe("shop", Map.of("item", snapshot(true, true, false)));
        long old = store.pending("shop").get("item").revision();
        store.observe("shop", Map.of());
        store.observe("shop", Map.of("item", snapshot(true, true, false)));
        assertFalse(store.acknowledge("shop", "item", old));
    }

    @Test
    void totalEntryBoundEvictsLeastRecentlyObservedShop() {
        var store = new TradeUpdateStore();
        Map<String, Snapshot> many = new LinkedHashMap<>();
        for (int i = 0; i < MAX_ENTRIES; i++) many.put("item" + i, snapshot(true, true, false));
        store.observe("first", many);
        for (int i = 0; i < 7; i++) store.observe("shop" + i, many);
        store.observe("third", Map.of("item", snapshot(true, true, false)));
        var serialized = store.serialize().getCompound("Shops");
        assertFalse(serialized.contains("first"));
        assertTrue(serialized.contains("shop0"));
        assertTrue(serialized.contains("third"));
    }
}
