package org.arcadia.arc_quest.quest.spec.io;

import org.arcadia.arc_quest.quest.spec.compile.QuestSpecCompiler;
import org.arcadia.arc_quest.quest.spec.validate.QuestSpecValidator;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.arcadia.arc_quest.data.sync.CollectionDefinitionSpecExporter;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.registry.CollectionFieldDemos;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import com.google.gson.Gson;
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

    @Test void javaAndInstallableExamplesHaveTheSameRulesContentAndRewardAmounts() throws Exception {
        var entries = CollectionFieldDemos.entries();
        for (var javaQuest : List.of(CollectionFieldDemos.field(entries), CollectionFieldDemos.renewable(entries), CollectionFieldDemos.parallel(entries))) {
            var jsonQuest = new QuestSpecCompiler().compile(read(javaQuest.getId().getPath()));
            // Normalize only presentation defaults that differ by construction and identity namespaces.
            // All executable targets/counts, content, payoff and sheet rules remain in the comparison.
            var javaSpec = CollectionDefinitionSpecExporter.quest(javaQuest, null);
            var jsonSpec = CollectionDefinitionSpecExporter.quest(jsonQuest, null);
            normalize(javaSpec); normalize(jsonSpec);
            assertEquals(new Gson().toJsonTree(javaSpec), new Gson().toJsonTree(jsonSpec), javaQuest.getId().toString());
        }
    }

    @Test void suppliesConsumeThreeOfSixAndHaveNoReplayableKnowledgePayments() throws Exception {
        var quest = new QuestSpecCompiler().compile(read("renewable_survey_demo"));
        assertTrue(quest.isRepeatable());
        assertEquals(1200, quest.getCollectionConfig().getRepeatCooldownTicks());
        var phase = quest.getPhase("round");
        assertEquals(6, phase.getCollectionSheet().getBindings().size());
        assertEquals(CollectionSheetCompletionPolicy.QUOTA, phase.getCollectionSheet().getCompletionPolicy());
        assertEquals(3, phase.getCollectionSheet().getRequiredCount());
        phase.getObjectives().forEach(objective -> assertEquals(ObjectiveType.OFFER, objective.getType()));
        phase.getCollectionSheet().getBindings().forEach(binding -> {
            assertTrue(binding.getRecordRequirements().isEmpty());
            assertTrue(binding.getRewards().isEmpty());
            assertTrue(binding.getOutcomeIds().isEmpty());
        });
    }

    @Test void campDistinguishesExistingPreparationFromNewCraftsAndConsumesItsFinalKit() throws Exception {
        var quest = new QuestSpecCompiler().compile(read("parallel_expedition_demo"));
        var preparation = quest.getPhase("preparation");
        preparation.getObjectives().forEach(objective -> {
            assertEquals(ObjectiveType.COLLECT, objective.getType());
            assertEquals("POSSESSION", objective.getExtra("collect_mode"));
        });
        assertEquals(List.of(List.of("wildlife"), List.of("materials")),
                preparation.getTransitions().stream().map(PhaseTransition::getTargetPhaseIds).toList());
        quest.getPhase("materials").getObjectives().forEach(objective -> assertEquals(ObjectiveType.CRAFT, objective.getType()));
        var report = quest.getPhase("report");
        assertTrue(report.getEnterCondition().dependsOnCurrentRun());
        report.getObjectives().forEach(objective -> assertEquals(ObjectiveType.OFFER, objective.getType()));
        assertEquals("minecraft:planks", report.getObjectives().get(0).getTargetTagId());
        assertFalse(report.shouldAutoAdvanceOnComplete());
    }

    private static QuestSpec read(String name) throws Exception {
        Path directory = Path.of(System.getProperty("arcq.test.projectDir"))
                .resolve("docs/examples/collection/collection-demo-pack/data/arc_quest_examples/arc_quest/quests");
        return QuestSpecJsonReader.read(Files.readString(directory.resolve(name + ".json")));
    }

    private static void normalize(QuestSpec spec) {
        // Theme remains user-editable; IDs differ intentionally between the two namespaces.
        spec.visualConfig.themeColor = 0;
        // Both values mean an unspecified phase theme; the JSON compiler masks RGB.
        for (var phase : spec.phases) if ((phase.visualConfig.themeColor & 0xFFFFFF) == 0xFFFFFF)
            phase.visualConfig.themeColor = -1;
        for (var entry : spec.collectionConfig.entries) {
            entry.legacyResearchObjectives.forEach(objective -> objective.displayText = org.arcadia.arc_quest.quest.spec.QuestTextSpec.literal(""));
        }
        // The strings being replaced are identity namespaces, not player text or asset paths.
        var normalized = QuestSpecJsonReader.read(QuestSpecJsonWriter.write(spec).replace("arc_quest_examples:", "arc_quest:"));
        spec.id = normalized.id;
        spec.collectionConfig = normalized.collectionConfig;
        spec.phases = normalized.phases;
    }
}
