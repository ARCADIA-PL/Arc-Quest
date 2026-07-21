package org.arcadia.arc_quest.core;

import org.arcadia.arc_quest.core.condition.ConditionProcessor;
import org.arcadia.arc_quest.core.state.ExpiringStateStore;
import org.arcadia.arc_quest.core.time.CooldownProcessor;

public interface CoreProcessor {

    ConditionProcessor conditions();

    CooldownProcessor cooldowns();

    <K, V> ExpiringStateStore<K, V> createExpiringStateStore();
}
