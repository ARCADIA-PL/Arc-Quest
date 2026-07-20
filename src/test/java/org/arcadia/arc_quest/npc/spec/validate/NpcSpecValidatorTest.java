package org.arcadia.arc_quest.npc.spec.validate;

import org.arcadia.arc_quest.npc.spec.NpcInteractionPolicy;
import org.arcadia.arc_quest.npc.spec.NpcSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcSpecValidatorTest {

    private final NpcSpecValidator validator = new NpcSpecValidator();

    @Test
    void implementedPoliciesAreAccepted() {
        NpcSpec spec = validSpec();
        spec.interactionPolicy = NpcInteractionPolicy.EXCLUSIVE;

        assertFalse(validator.validate(spec).hasErrors());
    }

    @Test
    void reservedPoliciesAreRejectedBeforePublishing() {
        NpcSpec spec = validSpec();
        spec.interactionPolicy = NpcInteractionPolicy.GROUP_SHARED;

        assertTrue(validator.validate(spec).hasErrors());
    }

    private static NpcSpec validSpec() {
        NpcSpec spec = new NpcSpec();
        spec.entityType = "minecraft:villager";
        return spec;
    }
}
