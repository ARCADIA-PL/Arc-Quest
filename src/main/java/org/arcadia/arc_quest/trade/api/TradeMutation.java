package org.arcadia.arc_quest.trade.api;

import java.util.Objects;

public interface TradeMutation {

    Reversibility reversibility();

    void commit();

    default void rollback() {
    }

    static TradeMutation nonReversible(Runnable operation) {
        Objects.requireNonNull(operation, "operation");
        return new TradeMutation() {
            @Override
            public Reversibility reversibility() {
                return Reversibility.NON_REVERSIBLE;
            }

            @Override
            public void commit() {
                operation.run();
            }
        };
    }

    enum Reversibility {
        REVERSIBLE,
        NON_REVERSIBLE
    }
}
