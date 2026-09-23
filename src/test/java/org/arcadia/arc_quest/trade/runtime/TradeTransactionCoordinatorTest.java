package org.arcadia.arc_quest.trade.runtime;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.trade.api.ITradeOffer;
import org.arcadia.arc_quest.trade.api.TradeMutation;
import org.arcadia.arc_quest.trade.api.TradeOfferRole;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class TradeTransactionCoordinatorTest {
    private final TradeTransactionCoordinator coordinator = new TradeTransactionCoordinator();

    @Test
    void partialCommitIncludingFailingOperationRollsBackInReverseOrder() {
        List<String> calls = new ArrayList<>();
        int[] balance = {10};
        var first = reversible(() -> { calls.add("pay1"); balance[0] -= 3; },
                () -> { calls.add("undo1"); balance[0] += 3; });
        var second = reversible(() -> {
            calls.add("pay2"); balance[0] -= 2; throw new IllegalStateException("Partial payment");
        }, () -> { calls.add("undo2"); balance[0] += 2; });
        var result = coordinator.execute(null, List.of(offer(first), offer(second)), List.of());
        assertFalse(result.succeeded());
        assertTrue(result.rollbackSucceeded());
        assertEquals(10, balance[0]);
        assertEquals(List.of("pay1", "pay2", "undo2", "undo1"), calls);
    }

    @Test
    void unreachedIrreversibleRewardDoesNotPreventCostRollback() {
        int[] balance = {10};
        var cost = reversible(() -> { balance[0]--; throw new IllegalStateException(); }, () -> balance[0]++);
        var reward = TradeMutation.nonReversible(() -> fail("Must not grant after failed payment"));
        var result = coordinator.execute(null, List.of(offer(cost)), List.of(offer(reward)));
        assertFalse(result.fullyReversible());
        assertTrue(result.rollbackSucceeded());
        assertEquals(10, balance[0]);
    }

    @Test
    void attemptedIrreversibleOperationPreventsUnsafePartialRollback() {
        List<String> calls = new ArrayList<>();
        var cost = reversible(() -> calls.add("pay"), () -> calls.add("undo"));
        var reward = TradeMutation.nonReversible(() -> {
            calls.add("partialReward"); throw new IllegalStateException();
        });
        var result = coordinator.execute(null, List.of(offer(cost)), List.of(offer(reward)));
        assertFalse(result.rollbackSucceeded());
        assertEquals(List.of("pay", "partialReward"), calls);
    }

    @Test
    void rollbackFailuresArePreservedAndDoNotStopOtherCompensation() {
        List<String> calls = new ArrayList<>();
        RuntimeException commitFailure = new IllegalStateException("Commit");
        RuntimeException rollbackFailure = new IllegalStateException("Rollback");
        var first = reversible(() -> {}, () -> calls.add("undo1"));
        var second = reversible(() -> { throw commitFailure; }, () -> { throw rollbackFailure; });
        var result = coordinator.execute(null, List.of(offer(first), offer(second)), List.of());
        assertSame(commitFailure, result.failure());
        assertArrayEquals(new Throwable[]{rollbackFailure}, result.failure().getSuppressed());
        assertFalse(result.rollbackSucceeded());
        assertEquals(List.of("undo1"), calls);
    }

    @Test
    void prepareFailureAndNullMutationCommitNothing() {
        var first = offer(reversible(() -> fail("Prepared only"), () -> fail("No commit to undo")));
        for (ITradeOffer bad : List.of(offer(role -> { throw new IllegalStateException("Prepare"); }),
                offer(role -> null))) {
            var result = coordinator.execute(null, List.of(first), List.of(bad));
            assertFalse(result.succeeded());
            assertTrue(result.rollbackSucceeded());
            assertNotNull(result.failure());
        }
    }

    @Test
    void reversibilityIsCapturedOnceBeforeAnyCommit() {
        int[] queries = {0}, balance = {10};
        var mutation = new TradeMutation() {
            public Reversibility reversibility() {
                if (++queries[0] > 1) throw new IllegalStateException("Unstable extension callback");
                return Reversibility.REVERSIBLE;
            }
            public void commit() { balance[0]--; throw new IllegalStateException("Partial payment"); }
            public void rollback() { balance[0]++; }
        };
        var result = coordinator.execute(null, List.of(offer(mutation)), List.of());
        assertTrue(result.rollbackSucceeded());
        assertEquals(10, balance[0]);
        assertEquals(1, queries[0]);
    }

    @Test
    void brokenReversibilityMetadataFailsBeforeCommit() {
        var mutation = new TradeMutation() {
            public Reversibility reversibility() { throw new IllegalStateException("Cannot prepare"); }
            public void commit() { fail("No payment may start"); }
        };
        var result = coordinator.execute(null, List.of(offer(mutation)), List.of());
        assertFalse(result.succeeded());
        assertTrue(result.rollbackSucceeded());
    }

    @Test
    void successPreparesAllRolesBeforeCommittingCostsThenRewards() {
        List<String> calls = new ArrayList<>();
        ITradeOffer offer = offer(role -> {
            calls.add("prepare:" + role);
            return reversible(() -> calls.add("commit:" + role), () -> fail("Successful transaction"));
        });
        var result = coordinator.execute(null, List.of(offer), List.of(offer));
        assertTrue(result.succeeded());
        assertTrue(result.fullyReversible());
        assertEquals(List.of("prepare:COST", "prepare:REWARD", "commit:COST", "commit:REWARD"), calls);
    }

    private static TradeMutation reversible(Runnable commit, Runnable rollback) {
        return new TradeMutation() {
            public Reversibility reversibility() { return Reversibility.REVERSIBLE; }
            public void commit() { commit.run(); }
            public void rollback() { rollback.run(); }
        };
    }

    private static ITradeOffer offer(TradeMutation mutation) { return offer(role -> mutation); }

    private static ITradeOffer offer(Function<TradeOfferRole, TradeMutation> prepare) {
        return new ITradeOffer() {
            public boolean canAfford(ServerPlayer player) { return true; }
            public void execute(ServerPlayer player) { fail("Should use prepared mutation"); }
            public Component describe() { return null; }
            public String getType() { return "test"; }
            public TradeMutation prepareMutation(ServerPlayer player, TradeOfferRole role) { return prepare.apply(role); }
        };
    }
}
