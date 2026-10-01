package org.arcadia.arc_quest.integration.jei.gacha;

import org.arcadia.arc_quest.trade.gacha.api.GachaItem;
import org.arcadia.arc_quest.trade.gacha.api.PityConfig;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class GachaOddsSnapshotTest {
    @Test void hiddenRewardsNeverReachResultsOrWeightCallbacks() {
        GachaItem hidden = item("secret", 90, GachaItem.Rarity.RARE);
        GachaItem visible = item("public", 10, GachaItem.Rarity.COMMON);
        var snapshot = new GachaOddsSnapshot(List.of(hidden, visible), item -> item != hidden, item -> {
            assertNotSame(hidden, item); return item.getBaseWeight();
        }, null, 0);
        assertEquals(1, snapshot.outcomes().size());
        assertSame(visible, snapshot.outcomes().get(0).item());
        assertEquals(100D, snapshot.outcomes().get(0).nextPercent());
    }
    @Test void ordinaryAndRarityPityUseDifferentAccurateDenominators() {
        var snapshot = new GachaOddsSnapshot(List.of(item("common", 90, GachaItem.Rarity.COMMON),
                item("rare_a", 6, GachaItem.Rarity.RARE), item("rare_b", 4, GachaItem.Rarity.RARE)),
                item -> true, GachaItem::getBaseWeight, new PityConfig(10, GachaItem.Rarity.RARE, true), 10);
        assertTrue(snapshot.pityActive());
        assertEquals(90D, snapshot.outcomes().get(0).ordinaryPercent());
        assertEquals(0D, snapshot.outcomes().get(0).nextPercent());
        assertEquals(6D, snapshot.outcomes().get(1).ordinaryPercent());
        assertEquals(60D, snapshot.outcomes().get(1).nextPercent());
        assertEquals(40D, snapshot.outcomes().get(2).nextPercent());
    }
    @Test void zeroWeightNamedPityIsStillGuaranteedIfVisible() {
        var snapshot = new GachaOddsSnapshot(List.of(item("normal", 1, GachaItem.Rarity.COMMON),
                item("named", 0, GachaItem.Rarity.RARE)), item -> true, GachaItem::getBaseWeight,
                new PityConfig(10, "named", true), 10);
        assertEquals(0D, snapshot.outcomes().get(1).ordinaryPercent());
        assertEquals(100D, snapshot.outcomes().get(1).nextPercent());
        assertTrue(snapshot.hasNextOutcome());
    }
    @Test void unavailableNamedPityDoesNotDiscloseOrReassignItsPrize() {
        var snapshot = new GachaOddsSnapshot(List.of(item("public", 10, GachaItem.Rarity.COMMON),
                item("secret", 10, GachaItem.Rarity.RARE)), item -> !item.getItemId().equals("secret"),
                GachaItem::getBaseWeight, new PityConfig(10, "secret", true), 10);
        assertEquals(1, snapshot.outcomes().size());
        assertEquals(0D, snapshot.outcomes().get(0).nextPercent());
        assertFalse(snapshot.hasNextOutcome());
    }
    @Test void probabilitiesAreFrozenAfterSingleEvaluationAndNeverRoll() {
        AtomicInteger visibility = new AtomicInteger(), weight = new AtomicInteger();
        GachaItem candidate = new GachaItem("a", null, null, 1, GachaItem.Rarity.COMMON, true) {
            @Override public int calculateActualCount() { throw new AssertionError("Preview cannot roll quantity"); }
        };
        var snapshot = new GachaOddsSnapshot(List.of(candidate), item -> { visibility.incrementAndGet(); return true; },
                item -> weight.incrementAndGet(), null, 0);
        assertEquals(100D, snapshot.outcomes().get(0).nextPercent());
        assertEquals(1, visibility.get());
        assertEquals(1, weight.get());
    }
    @Test void wideTotalsAndEmptyPoolsAreNotRoundedOrOverflowed() {
        var snapshot = new GachaOddsSnapshot(List.of(item("a", Integer.MAX_VALUE, GachaItem.Rarity.COMMON),
                item("b", Integer.MAX_VALUE, GachaItem.Rarity.COMMON)), item -> true, GachaItem::getBaseWeight, null, 0);
        assertEquals(50D, snapshot.outcomes().get(0).nextPercent());
        var empty = new GachaOddsSnapshot(List.of(item("a", -1, GachaItem.Rarity.COMMON)),
                item -> true, GachaItem::getBaseWeight, null, 0);
        assertFalse(empty.hasNextOutcome());
        assertEquals(0D, empty.outcomes().get(0).nextPercent());
    }
    @Test void beforeThresholdUsesOrdinaryWeightsAndBrokenConditionFailsTheWholeSnapshot() {
        var snapshot = new GachaOddsSnapshot(List.of(item("a", 2, GachaItem.Rarity.COMMON),
                item("b", 3, GachaItem.Rarity.RARE)), item -> true, GachaItem::getBaseWeight,
                new PityConfig(10, "b", true), 9);
        assertFalse(snapshot.pityActive());
        assertEquals(40D, snapshot.outcomes().get(0).nextPercent());
        assertThrows(IllegalStateException.class, () -> new GachaOddsSnapshot(
                List.of(item("bad", 1, GachaItem.Rarity.COMMON)), item -> { throw new IllegalStateException(); },
                GachaItem::getBaseWeight, null, 0));
    }
    private static GachaItem item(String id, int weight, GachaItem.Rarity rarity) {
        return new GachaItem(id, null, null, weight, rarity, true);
    }
}
