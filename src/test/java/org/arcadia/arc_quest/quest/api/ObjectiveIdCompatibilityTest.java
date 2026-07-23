package org.arcadia.arc_quest.quest.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.builder.PhaseBuilder;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonReader;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObjectiveIdCompatibilityTest {

    @Test
    void legacyObjectivesReceiveDeterministicIdsByPhaseOrder() {
        PhaseDefinition phase = PhaseBuilder.create("phase_one")
                .displayName("Phase")
                .objective(legacyObjective("first"))
                .objective(legacyObjective("second"))
                .build();

        assertEquals("objective_1", phase.getObjectives().get(0).getObjectiveId());
        assertEquals("objective_2", phase.getObjectives().get(1).getObjectiveId());
        assertEquals(1, phase.getObjectiveIndex("objective_2"));
        assertNotNull(phase.getObjective("objective_1"));
    }

    @Test
    void explicitDuplicateIdsAreRejected() {
        ObjectiveEntry first = legacyObjective("first").withObjectiveId("stable");
        ObjectiveEntry second = legacyObjective("second").withObjectiveId("stable");

        assertThrows(IllegalArgumentException.class, () -> PhaseBuilder.create("phase_one")
                .displayName("Phase")
                .objective(first)
                .objective(second)
                .build());
    }

    @Test
    void jsonReaderPreservesObjectiveId() {
        QuestSpec spec = QuestSpecJsonReader.read("""
                {"phases":[{"objectives":[{"id":"talk_to_guard"}]}]}
                """);

        assertEquals("talk_to_guard", spec.phases.get(0).objectives.get(0).id);
    }

    private static ObjectiveEntry legacyObjective(String targetPath) {
        return new ObjectiveEntry(
                ObjectiveType.CUSTOM,
                ResourceLocation.fromNamespaceAndPath("arc_quest", targetPath),
                1,
                Component.literal(targetPath),
                false,
                false,
                Map.of());
    }
}
