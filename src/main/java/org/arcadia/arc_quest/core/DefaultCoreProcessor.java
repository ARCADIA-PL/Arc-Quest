package org.arcadia.arc_quest.core;

import org.arcadia.arc_quest.core.condition.ConditionProcessor;
import org.arcadia.arc_quest.core.state.ExpiringStateStore;
import org.arcadia.arc_quest.core.time.CooldownProcessor;

final class DefaultCoreProcessor implements CoreProcessor {

    private final ConditionProcessor conditions = ConditionProcessor.createDefault();
    private final CooldownProcessor cooldowns = CooldownProcessor.createDefault();

    @Override
    public ConditionProcessor conditions() {
        return conditions;
    }

    @Override
    public CooldownProcessor cooldowns() {
        return cooldowns;
    }

    @Override
    public <K, V> ExpiringStateStore<K, V> createExpiringStateStore() {
        return new ExpiringStateStore<>();
    }
}
