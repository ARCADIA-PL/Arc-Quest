package org.arcadia.arc_quest.quest.spec.io;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.data.sync.CollectionDefinitionSpecExporter;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.registry.CollectionEntryRegistry;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.arcadia.arc_quest.quest.spec.compile.QuestSpecCompiler;
import org.arcadia.arc_quest.quest.spec.validate.QuestSpecValidator;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UnifiedCollectionSpecTest {
    @BeforeAll static void initialize() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void unifiedSourcesContentRewardsAndMigrationThresholdsSurviveJsonAndExport() {
        QuestSpec spec = valid();
        var report = new QuestSpecValidator().validate(spec);
        assertFalse(report.hasErrors(), () -> report.getIssues().toString());
        QuestSpec decoded = QuestSpecJsonReader.read(QuestSpecJsonWriter.write(spec));
        for (var quest : List.of(new QuestSpecCompiler().compile(decoded), QuestSpecCompiler.compileClientPresentation(decoded, Map.of()))) {
            var entry = quest.getCollectionConfig().getEntries().get(0);
            assertTrue(entry.isUnifiedGameplay());
            assertTrue(entry.getResearchObjectives().isEmpty());
            assertEquals("Anatomy", entry.getOutcome("anatomy").getDisplayName().getString());
            assertEquals(5, entry.getLegacyResearchObjectives().get(0).getRequiredCount());
            assertEquals("anatomy", entry.getLegacyResearchOutcomeMappings().get("old_study"));
            assertEquals(CollectionContentReveal.OUTCOME, entry.getContent().get(0).reveal());
            assertEquals("anatomy", entry.getRewards().get(0).outcomeId());
            assertEquals(CollectionRewardPreviewVisibility.PUBLIC, entry.getRewards().get(0).previewVisibility());
            var binding = quest.getPhase("survey").getCollectionSheet().getBindings().get(0);
            assertEquals(List.of("anatomy"), binding.getOutcomeIds());
            assertEquals("payment", binding.getRewards().get(0).rewardId());
            QuestSpec exported = QuestSpecJsonReader.read(QuestSpecJsonWriter.write(CollectionDefinitionSpecExporter.quest(quest, null)));
            assertEquals(2, exported.collectionConfig.entries.get(0).gameplayVersion);
            assertEquals("anatomy", exported.phases.get(0).collectionSheet.bindings.get(0).outcomeIds.get(0));
            assertEquals("PUBLIC", exported.phases.get(0).collectionSheet.bindings.get(0).rewards.get(0).previewVisibility);
        }
    }

    @Test void oldJsonWithoutVersionRemainsExplicitLegacyRules() {
        var spec = valid();
        var tree = com.google.gson.JsonParser.parseString(QuestSpecJsonWriter.write(spec)).getAsJsonObject();
        var entry = tree.getAsJsonObject("collectionConfig").getAsJsonArray("entries").get(0).getAsJsonObject();
        entry.remove("gameplayVersion"); entry.remove("outcomes"); entry.remove("legacyResearchOutcomeMappings"); entry.remove("legacyResearchObjectives");
        entry.remove("content"); entry.remove("rewards");
        var binding = tree.getAsJsonArray("phases").get(0).getAsJsonObject().getAsJsonObject("collectionSheet").getAsJsonArray("bindings").get(0).getAsJsonObject();
        binding.remove("outcomeIds"); binding.remove("rewards");
        var quest = new QuestSpecCompiler().compile(QuestSpecJsonReader.read(tree.toString()));
        assertFalse(quest.getCollectionConfig().getEntries().get(0).isUnifiedGameplay());
        assertEquals(1, quest.getCollectionConfig().getEntries().get(0).getGameplayVersion());
    }

    @Test void ambiguousResearchUnknownSourcesAndFactOnlyPaymentsAreRejected() {
        var mixed = valid(); mixed.collectionConfig.entries.get(0).researchObjectives.add(mixed.collectionConfig.entries.get(0).legacyResearchObjectives.get(0));
        assertTrue(new QuestSpecValidator().validate(mixed).hasErrors());
        var unknown = valid(); unknown.phases.get(0).collectionSheet.bindings.get(0).outcomeIds.set(0, "missing");
        assertTrue(new QuestSpecValidator().validate(unknown).hasErrors());
        var noThreshold = valid(); noThreshold.collectionConfig.entries.get(0).legacyResearchObjectives.clear();
        assertTrue(new QuestSpecValidator().validate(noThreshold).hasErrors());
        var freePayment = valid(); freePayment.phases.get(0).collectionSheet.bindings.get(0).objectiveIds.clear();
        assertTrue(new QuestSpecValidator().validate(freePayment).hasErrors());
        var any = valid(); any.phases.get(0).collectionSheet.bindings.get(0).requirementMode = "ANY";
        assertTrue(new QuestSpecValidator().validate(any).hasErrors());
        var wronglyScoped = valid(); wronglyScoped.phases.get(0).collectionSheet.bindings.get(0).rewards.get(0).trigger = "OUTCOME";
        assertTrue(new QuestSpecValidator().validate(wronglyScoped).hasErrors());
    }

    @Test void snapshotPublishesExplicitSourcesAndRejectsOrphanBeforeChangingDefinitions() {
        var quest = new QuestSpecCompiler().compile(valid());
        var previousQuests = List.copyOf(org.arcadia.arc_quest.quest.registry.QuestRegistry.getAll());
        try {
            CollectionEntryRegistry.replaceQuestSnapshot(List.of(quest));
            var sources = CollectionEntryRegistry.getOutcomeSources(ResourceLocation.parse("example:unified_zombie"), "anatomy");
            assertEquals(List.of(new CollectionOutcomeSource(quest.getId(), "survey", "investigation")), sources);
            var orphan = valid(); orphan.phases.get(0).collectionSheet.bindings.get(0).outcomeIds.clear();
            assertThrows(IllegalArgumentException.class, () -> CollectionEntryRegistry.replaceQuestSnapshot(List.of(new QuestSpecCompiler().compile(orphan))));
            assertEquals(sources, CollectionEntryRegistry.getOutcomeSources(ResourceLocation.parse("example:unified_zombie"), "anatomy"));
        } finally {
            // Preserve definitions registered by other suites; none of the temporary entries enters CODE.
            CollectionEntryRegistry.replaceQuestSnapshot(previousQuests);
        }
    }

    private static QuestSpec valid() {
        return QuestSpecJsonReader.read("""
            {"id":"example:unified_collection","mode":"COLLECTION","initialPhaseId":"survey",
             "collectionConfig":{"categories":[{"categoryId":"mobs"}],"entries":[{
               "entryId":"example:unified_zombie","categoryId":"mobs","gameplayVersion":2,
               "outcomes":[{"outcomeId":"anatomy","displayName":{"mode":"literal","value":"Anatomy"}}],
               "legacyResearchObjectives":[{"id":"old_study","type":"arc_quest:custom","targetId":"example:study","requiredCount":5}],
               "legacyResearchOutcomeMappings":{"old_study":"anatomy"},
               "content":[{"blockId":"notes","reveal":"OUTCOME","revealStepId":"anatomy","text":{"mode":"literal","value":"Notes"}}],
               "rewards":[{"rewardId":"first_notes","trigger":"OUTCOME","outcomeId":"anatomy","previewVisibility":"PUBLIC","rewards":[]}]
             }]},"phases":[{"phaseId":"survey",
               "objectives":[{"id":"action","type":"arc_quest:custom","targetId":"example:action","requiredCount":3}],
               "collectionSheet":{"bindings":[{"bindingId":"investigation","entryId":"example:unified_zombie","objectiveIds":["action"],
                 "recordRequirements":[{"type":"DISCOVERED"}],"outcomeIds":["anatomy"],
                 "rewards":[{"rewardId":"payment","trigger":"BINDING_COMPLETE","previewVisibility":"PUBLIC","rewards":[]}]
               }]}}]}
            """);
    }
}
