package org.arcadia.arc_quest.trade.gacha.api;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GachaWeightSnapshotTest {
    @Test
    void ticketBoundariesAndProbabilitiesUseIdenticalWeights() {
        GachaItem a = item("a", 2), b = item("b", 3);
        var snapshot = new GachaWeightSnapshot(List.of(a, b), item -> true, GachaItem::getBaseWeight);
        assertSame(a, snapshot.draw(total -> { assertEquals(5, total); return 0; }));
        assertSame(a, snapshot.draw(total -> 1));
        assertSame(b, snapshot.draw(total -> 2));
        assertSame(b, snapshot.draw(total -> 4));
        assertEquals(40.0, snapshot.probability("a"));
        assertEquals(60.0, snapshot.probability("b"));
        assertEquals(0.0, snapshot.probability("missing"));
        assertThrows(IllegalArgumentException.class, () -> snapshot.draw(total -> -1));
        assertThrows(IllegalArgumentException.class, () -> snapshot.draw(total -> total));
    }

    @Test
    void invisibleAndNonPositiveItemsCannotWin() {
        GachaItem hidden = item("hidden", 100), zero = item("zero", 0), negative = item("negative", -3);
        var snapshot = new GachaWeightSnapshot(List.of(hidden, zero, negative), item -> item != hidden,
                item -> { assertNotSame(hidden, item); return item.getBaseWeight(); });
        assertNull(snapshot.draw(total -> { fail("Empty pool must not call random"); return 0; }));
        assertEquals(0.0, snapshot.probability("hidden"));
    }

    @Test
    void mutableConditionsAndWeightsAreEvaluatedOncePerSnapshot() {
        int[] visibleCalls = {0}, weightCalls = {0};
        var snapshot = new GachaWeightSnapshot(List.of(item("a", 1)),
                item -> ++visibleCalls[0] == 1, item -> ++weightCalls[0] == 1 ? 5 : 0);
        assertNotNull(snapshot.draw(total -> total - 1));
        assertEquals(100, snapshot.probability("a"));
        assertEquals(1, visibleCalls[0]);
        assertEquals(1, weightCalls[0]);
    }

    @Test
    void totalWeightCanExceedIntegerRange() {
        GachaItem a = item("a", Integer.MAX_VALUE), b = item("b", Integer.MAX_VALUE);
        var snapshot = new GachaWeightSnapshot(List.of(a, b), item -> true, GachaItem::getBaseWeight);
        assertSame(b, snapshot.draw(total -> {
            assertEquals(2L * Integer.MAX_VALUE, total);
            return total - 1;
        }));
        assertEquals(50.0, snapshot.probability("a"));
        assertEquals(Integer.MAX_VALUE, new GachaPool(List.of(a, b)).calculateTotalWeight(null));
    }

    @Test
    void rarityDrawUsesContextAwareWeightOverloadExactlyOnce() {
        int[] calls = {0};
        GachaItem candidate = new GachaItem("a", null, null, 1, GachaItem.Rarity.RARE, false) {
            @Override
            public int getEffectiveWeight(ArcQuestPlayer data) { throw new AssertionError("Lost server context"); }
            @Override
            public int getEffectiveWeight(ServerPlayer player, ArcQuestPlayer data) {
                assertEquals(1, ++calls[0]);
                return 1;
            }
        };
        assertSame(candidate, new GachaPool(List.of(candidate)).drawFromRarity(GachaItem.Rarity.RARE, null, null));
    }

    @Test
    void effectiveWeightAccumulatesWithoutIntegerOverflow() {
        GachaItem candidate = item("a", Integer.MAX_VALUE);
        candidate.getWeightModifiers().add(new GachaItem.WeightModifier(null, Integer.MAX_VALUE) {
            @Override
            public boolean matches(ServerPlayer player, ArcQuestPlayer data) { return true; }
        });
        assertEquals(Integer.MAX_VALUE, candidate.getEffectiveWeight(null));
        candidate.getWeightModifiers().add(new GachaItem.WeightModifier(null, Integer.MIN_VALUE) {
            @Override
            public boolean matches(ServerPlayer player, ArcQuestPlayer data) { return true; }
        });
        assertEquals(Integer.MAX_VALUE - 1, candidate.getEffectiveWeight(null));
    }

    @Test
    void randomCountSupportsInclusiveIntegerMaximum() {
        GachaItem candidate = new GachaItem("a", null, null, 1, GachaItem.Rarity.COMMON, false,
                Integer.MAX_VALUE - 1, Integer.MAX_VALUE, null, null, -1, null, 0);
        for (int i = 0; i < 50; i++) assertTrue(candidate.calculateActualCount() >= Integer.MAX_VALUE - 1);
    }

    private static GachaItem item(String id, int weight) {
        return new GachaItem(id, null, null, weight, GachaItem.Rarity.COMMON, false);
    }
}
