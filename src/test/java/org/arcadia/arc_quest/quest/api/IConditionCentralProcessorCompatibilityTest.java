package org.arcadia.arc_quest.quest.api;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IConditionCentralProcessorCompatibilityTest {

    @Test
    void existingCompositionMethodsKeepBehavior() {
        ICondition positive = (player, quests, flags, variables) -> true;
        ICondition negative = (player, quests, flags, variables) -> false;

        assertFalse(positive.and(negative).test(null, Set.of(), Set.of(), Map.of()));
        assertTrue(positive.or(negative).test(null, Set.of(), Set.of(), Map.of()));
        assertTrue(negative.negate().test(null, Set.of(), Set.of(), Map.of()));
    }
}
