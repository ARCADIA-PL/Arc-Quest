package org.arcadia.arc_quest.trade.runtime;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.trade.api.ITradeOffer;
import org.arcadia.arc_quest.trade.api.TradeMutation;
import org.arcadia.arc_quest.trade.api.TradeOfferRole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class TradeTransactionCoordinator {

    public TransactionResult execute(ServerPlayer player,
                                     List<ITradeOffer> costs,
                                     List<ITradeOffer> rewards) {
        List<PreparedMutation> mutations = new ArrayList<>(costs.size() + rewards.size());
        try {
            var itemCosts=costs.stream().filter(c->c instanceof org.arcadia.arc_quest.trade.offer.ItemTradeOffer).map(c->(org.arcadia.arc_quest.trade.offer.ItemTradeOffer)c).toList();
            if(!itemCosts.isEmpty()) {
                var rows=new ArrayList<org.arcadia.arc_quest.inventory.CostPlan.Requirement>();int index=0;
                for(var cost:itemCosts)rows.add(new org.arcadia.arc_quest.inventory.CostPlan.Requirement("cost/"+index++,cost.getDisplayCount(player),cost::matchesStack));
                var quote=new org.arcadia.arc_quest.inventory.CostPlan(rows).quote(player);
                if(!quote.affordable())throw new IllegalStateException("Insufficient combined item costs");
                mutations.add(new PreparedMutation(new TradeMutation(){
                    org.arcadia.arc_quest.inventory.PlayerItemAccess.Removal payment;
                    public Reversibility reversibility(){return Reversibility.REVERSIBLE;}
                    public void commit(){payment=quote.pay(player);}
                    public void rollback(){if(payment!=null)payment.rollback();}
                },true));
            }
            for (ITradeOffer cost : costs) if(!(cost instanceof org.arcadia.arc_quest.trade.offer.ItemTradeOffer)) mutations.add(prepare(cost, player, TradeOfferRole.COST));
            for (ITradeOffer reward : rewards) {
                mutations.add(prepare(reward, player, TradeOfferRole.REWARD));
            }
        } catch (RuntimeException exception) {
            return TransactionResult.failure(false, true, exception);
        }

        boolean fullyReversible = mutations.stream()
                .allMatch(PreparedMutation::reversible);
        List<PreparedMutation> committed = new ArrayList<>(mutations.size());
        try {
            for (PreparedMutation mutation : mutations) {
                // commit 自身可能部分生效再抛错，失败项也必须参加回滚。
                committed.add(mutation);
                mutation.operation().commit();
            }
            return TransactionResult.success(fullyReversible);
        } catch (RuntimeException exception) {
            boolean rollbackSucceeded = committed.stream()
                    .allMatch(PreparedMutation::reversible)
                    && rollback(committed, exception);
            return TransactionResult.failure(fullyReversible, rollbackSucceeded, exception);
        }
    }

    private static PreparedMutation prepare(ITradeOffer offer, ServerPlayer player, TradeOfferRole role) {
        TradeMutation operation = Objects.requireNonNull(offer.prepareMutation(player, role), "trade mutation");
        // 扩展能力也在准备阶段固定，不能在扣费失败后再次执行附属回调来决定能否回滚。
        var reversibility = Objects.requireNonNull(operation.reversibility(), "mutation reversibility");
        return new PreparedMutation(operation, reversibility == TradeMutation.Reversibility.REVERSIBLE);
    }

    private record PreparedMutation(TradeMutation operation, boolean reversible) { }

    private boolean rollback(List<PreparedMutation> committed, RuntimeException originalFailure) {
        List<PreparedMutation> reverseOrder = new ArrayList<>(committed);
        Collections.reverse(reverseOrder);
        boolean succeeded = true;
        for (PreparedMutation mutation : reverseOrder) {
            try {
                mutation.operation().rollback();
            } catch (RuntimeException failure) {
                if (failure != originalFailure) originalFailure.addSuppressed(failure);
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
