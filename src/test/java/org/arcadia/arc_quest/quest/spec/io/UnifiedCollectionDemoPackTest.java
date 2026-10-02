package org.arcadia.arc_quest.quest.spec.io;

import org.arcadia.arc_quest.quest.spec.compile.QuestSpecCompiler;
import org.arcadia.arc_quest.quest.spec.validate.QuestSpecValidator;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class UnifiedCollectionDemoPackTest {
    @BeforeAll static void setup() { MinecraftRegistryTestBootstrap.initialize(); }
    @Test void publishedDemoPackValidatesAndCompilesAsUnifiedInvestigations() throws Exception {
        Path directory = Path.of(System.getProperty("arcq.test.projectDir")).resolve("docs/examples/collection/collection-demo-pack/data/arc_quest_examples/arc_quest/quests");
        for (String name : List.of("field_compendium_demo", "renewable_survey_demo", "parallel_expedition_demo")) {
            var spec = QuestSpecJsonReader.read(Files.readString(directory.resolve(name + ".json")));
            var validation = new QuestSpecValidator().validate(spec);
            assertFalse(validation.hasErrors(), () -> name + ": " + validation);
            var compiled = new QuestSpecCompiler().compile(spec);
            assertTrue(compiled.hasCollectionSheets());
            compiled.getCollectionConfig().getEntries().forEach(entry -> {
                assertTrue(entry.isUnifiedGameplay()); assertTrue(entry.getResearchObjectives().isEmpty());
            });
            if (name.equals("field_compendium_demo")) {
                assertEquals(List.of("anatomy"), compiled.getPhase("survey").getCollectionSheet().getBinding("zombie").getOutcomeIds());
                assertEquals(1, compiled.getPhase("survey").getCollectionSheet().getBinding("zombie").getRewards().size());
            }
            if (name.equals("parallel_expedition_demo")) assertTrue(compiled.getPhase("report").getEnterCondition().dependsOnCurrentRun());
        }
    }
}
