package org.arcadia.arc_quest.trade.runtime;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.trade.api.ITradeOffer;
import org.arcadia.arc_quest.trade.api.TradeMutation;
import org.arcadia.arc_quest.trade.api.TradeOfferRole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TradeTransactionCoordinator {

    public TransactionResult execute(ServerPlayer player,
                                     List<ITradeOffer> costs,
                                     List<ITradeOffer> rewards) {
        List<TradeMutation> mutations = new ArrayList<>(costs.size() + rewards.size());
        try {
            for (ITradeOffer cost : costs) {
                mutations.add(cost.prepareMutation(player, TradeOfferRole.COST));
            }
            for (ITradeOffer reward : rewards) {
                mutations.add(reward.prepareMutation(player, TradeOfferRole.REWARD));
            }
        } catch (RuntimeException exception) {
            return TransactionResult.failure(false, false, exception);
        }

        boolean fullyReversible = mutations.stream()
                .allMatch(mutation -> mutation.reversibility() == TradeMutation.Reversibility.REVERSIBLE);
        List<TradeMutation> committed = new ArrayList<>(mutations.size());
        try {
            for (TradeMutation mutation : mutations) {
                mutation.commit();
                committed.add(mutation);
            }
            return TransactionResult.success(fullyReversible);
        } catch (RuntimeException exception) {
            boolean rollbackSucceeded = fullyReversible && rollback(committed);
            return TransactionResult.failure(fullyReversible, rollbackSucceeded, exception);
        }
    }

    private boolean rollback(List<TradeMutation> committed) {
        List<TradeMutation> reverseOrder = new ArrayList<>(committed);
        Collections.reverse(reverseOrder);
        boolean succeeded = true;
        for (TradeMutation mutation : reverseOrder) {
            try {
                mutation.rollback();
            } catch (RuntimeException ignored) {
                succeeded = false;
            }
        }
        return succeeded;
    }

    public record TransactionResult(boolean succeeded,
                                    boolean fullyReversible,
                                    boolean rollbackSucceeded,
                                    RuntimeException failure) {
        public static TransactionResult success(boolean fullyReversible) {
            return new TransactionResult(true, fullyReversible, false, null);
        }

        public static TransactionResult failure(boolean fullyReversible,
                                                boolean rollbackSucceeded,
                                                RuntimeException failure) {
            return new TransactionResult(false, fullyReversible, rollbackSucceeded, failure);
        }
    }
}
