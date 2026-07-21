package org.arcadia.arc_quest.core.condition;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionProcessorTest {

    private final ConditionProcessor processor = ConditionProcessor.createDefault();

    @Test
    void allAndAnyPreserveShortCircuitEvaluation() {
        AtomicInteger evaluations = new AtomicInteger();
        CoreCondition<String> positive = context -> {
            evaluations.incrementAndGet();
            return true;
        };
        CoreCondition<String> negative = context -> {
            evaluations.incrementAndGet();
            return false;
        };

        assertFalse(processor.all(List.of(negative, positive), "context"));
        assertTrue(processor.any(List.of(positive, negative), "context"));
        assertTrue(processor.none(negative, "context"));
        assertEquals(3, evaluations.get());
    }
}
