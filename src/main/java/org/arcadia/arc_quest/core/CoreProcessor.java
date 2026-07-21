package org.arcadia.arc_quest.core;

import org.arcadia.arc_quest.core.condition.ConditionProcessor;
import org.arcadia.arc_quest.core.state.ExpiringStateStore;
import org.arcadia.arc_quest.core.time.CooldownProcessor;
import org.arcadia.arc_quest.core.time.TimeProcessor;

public interface CoreProcessor {

    ConditionProcessor conditions();

    CooldownProcessor cooldowns();

    default TimeProcessor time() {
        return TimeProcessor.system();
    }

    <K, V> ExpiringStateStore<K, V> createExpiringStateStore();
}
