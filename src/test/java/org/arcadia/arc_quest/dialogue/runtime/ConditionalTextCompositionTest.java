package org.arcadia.arc_quest.dialogue.runtime;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ConditionalTextCompositionTest {
    @Test void nestedAndLegacyConditionsKeepTheirBranches() {
        assertEquals(List.of("(ALL:(HAS_FLAG:a);(NOT:HAS_FLAG:b))", "(HAS_FLAG:c)"),
                ConditionalTextEvaluator.splitConditions("(ALL:(HAS_FLAG:a);(NOT:HAS_FLAG:b));(HAS_FLAG:c)"));
        assertEquals(List.of("HAS_FLAG:a", "HAS_FLAG:b"),
                ConditionalTextEvaluator.splitConditions("HAS_FLAG:a;HAS_FLAG:b"));
        assertThrows(IllegalArgumentException.class, () -> ConditionalTextEvaluator.splitConditions("(a;b"));
    }
}
