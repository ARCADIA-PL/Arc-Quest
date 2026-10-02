package org.arcadia.arc_quest.quest.api;

import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.data.sync.CollectionDefinitionSpecExporter;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.quest.spec.ObjectiveSpec;
import org.arcadia.arc_quest.quest.spec.PhaseSpec;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.arcadia.arc_quest.quest.spec.compile.QuestSpecCompiler;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonReader;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonWriter;
import org.arcadia.arc_quest.quest.spec.validate.QuestSpecValidator;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ObjectiveCollectModeTest {
    @BeforeAll static void setup() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void existingCollectKeepsItsLegacyAcquisitionMeaning() {
        var objective = ObjectiveBuilder.collect(Items.APPLE, 3).build();
        assertEquals(CollectMode.LEGACY_ACQUISITION, CollectMode.from(objective));
        assertTrue(CollectMode.from(objective).acceptsAcquisition(false));
        assertTrue(CollectMode.from(objective).acceptsAcquisition(true));
    }

    @Test void possessionSurvivesExportJsonAndCompilationWithoutInventingAccumulatedProgress() {
        var source = spec(CollectionDefinitionSpecExporter.objective(ObjectiveBuilder.possess(Items.APPLE, 3).build(), null));
        assertFalse(new QuestSpecValidator().validate(source).hasErrors());
        var restored = QuestSpecJsonReader.read(QuestSpecJsonWriter.write(source));
        var objective = new QuestSpecCompiler().compile(restored).getPhase("prepare").getObjectives().get(0);
        assertEquals(CollectMode.POSSESSION, CollectMode.from(objective));
        assertFalse(CollectMode.from(objective).acceptsAcquisition(false));
        assertFalse(CollectMode.from(objective).acceptsAcquisition(true));
    }

    @Test void explicitJsonModeTakesPrecedenceAndInvalidOrWrongTypeModesAreRejected() {
        var objective = CollectionDefinitionSpecExporter.objective(ObjectiveBuilder.collect(Items.APPLE, 3).build(), null);
        objective.collectMode = "crafted_only";
        var compiled = new QuestSpecCompiler().compile(spec(objective)).getPhase("prepare").getObjectives().get(0);
        assertEquals(CollectMode.CRAFTED_ONLY, CollectMode.from(compiled));
        objective.collectMode = "unknown";
        assertTrue(new QuestSpecValidator().validate(spec(objective)).hasErrors());
        objective.collectMode = "POSSESSION";
        objective.type = ObjectiveType.CRAFT.getId().toString();
        assertTrue(new QuestSpecValidator().validate(spec(objective)).hasErrors());
        assertThrows(IllegalStateException.class,
                () -> ObjectiveBuilder.craft(Items.APPLE, 1).collectMode(CollectMode.POSSESSION));
    }

    private static QuestSpec spec(ObjectiveSpec objective) {
        var source = new QuestSpec(); source.id = "example:collect_mode";
        source.category = "arc_quest:adventure"; source.initialPhaseId = "prepare";
        var phase = new PhaseSpec(); phase.phaseId = "prepare"; phase.objectives = List.of(objective);
        source.phases = List.of(phase);
        return source;
    }
}
