package org.arcadia.arc_quest.npc.spec.io;

import org.arcadia.arc_quest.npc.spec.NpcInteractionPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcSpecJsonReaderTest {

    @Test
    void missingPolicyUsesBackwardCompatibleDefault() {
        var spec = NpcSpecJsonReader.read("{\"entityType\":\"minecraft:villager\",\"bindings\":[]}");

        assertEquals(NpcInteractionPolicy.PARALLEL_PRIVATE, spec.interactionPolicy);
    }

    @Test
    void explicitExclusivePolicyIsParsed() {
        var spec = NpcSpecJsonReader.read("{\"entityType\":\"minecraft:villager\",\"bindings\":[],\"interactionPolicy\":\"EXCLUSIVE\"}");

        assertEquals(NpcInteractionPolicy.EXCLUSIVE, spec.interactionPolicy);
    }
}
