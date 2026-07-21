package org.arcadia.arc_quest.core.execution;

import java.util.Objects;

final class DefaultExecutionProcessor implements ExecutionProcessor {

    static final DefaultExecutionProcessor INSTANCE = new DefaultExecutionProcessor();

    private DefaultExecutionProcessor() {
    }

    @Override
    public <C, F> CoreDecision<F> decide(C context, Iterable<? extends CoreRule<C, F>> rules) {
        Objects.requireNonNull(rules, "rules");
        for (CoreRule<C, F> rule : rules) {
            CoreDecision<F> decision = Objects.requireNonNull(rule, "rule").evaluate(context);
            if (!Objects.requireNonNull(decision, "decision").allowed()) return decision;
        }
        return CoreDecision.allow();
    }
}
