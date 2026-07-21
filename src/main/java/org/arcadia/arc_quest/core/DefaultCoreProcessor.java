package org.arcadia.arc_quest.core;

import org.arcadia.arc_quest.core.condition.ConditionProcessor;
import org.arcadia.arc_quest.core.execution.ExecutionProcessor;
import org.arcadia.arc_quest.core.state.ExpiringStateStore;
import org.arcadia.arc_quest.core.time.CooldownProcessor;
import org.arcadia.arc_quest.core.time.TimeProcessor;

final class DefaultCoreProcessor implements CoreProcessor {

    private final ConditionProcessor conditions = ConditionProcessor.createDefault();
    private final CooldownProcessor cooldowns = CooldownProcessor.createDefault();
    private final TimeProcessor time = TimeProcessor.system();
    private final ExecutionProcessor executions = ExecutionProcessor.createDefault();

    @Override
    public ConditionProcessor conditions() {
        return conditions;
    }

    @Override
    public CooldownProcessor cooldowns() {
        return cooldowns;
    }

    @Override
    public TimeProcessor time() {
        return time;
    }

    @Override
    public ExecutionProcessor executions() {
        return executions;
    }

    @Override
    public <K, V> ExpiringStateStore<K, V> createExpiringStateStore() {
        return new ExpiringStateStore<>();
    }
}
