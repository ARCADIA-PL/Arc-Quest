package org.arcadia.arc_quest.trade.runtime;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.trade.api.ITradeOffer;
import org.arcadia.arc_quest.trade.api.TradeMutation;
import org.arcadia.arc_quest.trade.api.TradeOfferRole;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeTransactionCoordinatorTest {

    @Test
    void fullyReversibleTransactionRollsBackInReverseOrder() {
        List<String> calls = new ArrayList<>();
        ITradeOffer first = reversible("first", calls, false);
        ITradeOffer second = reversible("second", calls, true);

        var result = new TradeTransactionCoordinator().execute(null, List.of(first, second), List.of());

        assertFalse(result.succeeded());
        assertTrue(result.fullyReversible());
        assertTrue(result.rollbackSucceeded());
        assertEquals(List.of("commit:first", "commit:second", "rollback:first"), calls);
    }

    @Test
    void legacyOfferFailureIsReportedWithoutFalseRollbackClaim() {
        ITradeOffer legacy = new TestOffer() {
            @Override
            public void execute(ServerPlayer player) {
                throw new IllegalStateException("legacy failure");
            }
        };

        var result = new TradeTransactionCoordinator().execute(null, List.of(legacy), List.of());

        assertFalse(result.succeeded());
        assertFalse(result.fullyReversible());
        assertFalse(result.rollbackSucceeded());
    }

    private static ITradeOffer reversible(String name, List<String> calls, boolean fail) {
        return new TestOffer() {
            @Override
            public TradeMutation prepareMutation(ServerPlayer player, TradeOfferRole role) {
                return new TradeMutation() {
                    @Override
                    public Reversibility reversibility() {
                        return Reversibility.REVERSIBLE;
                    }

                    @Override
                    public void commit() {
                        calls.add("commit:" + name);
                        if (fail) throw new IllegalStateException(name);
                    }

                    @Override
                    public void rollback() {
                        calls.add("rollback:" + name);
                    }
                };
            }
        };
    }

    private abstract static class TestOffer implements ITradeOffer {
        @Override
        public boolean canAfford(ServerPlayer player) {
            return true;
        }

        @Override
        public void execute(ServerPlayer player) {
        }

        @Override
        public Component describe() {
            return Component.empty();
        }

        @Override
        public String getType() {
            return "test";
        }
    }
}
