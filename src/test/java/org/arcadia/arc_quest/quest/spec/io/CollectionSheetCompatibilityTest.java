package org.arcadia.arc_quest.quest.spec.io;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.CollectionEntryBuilder;
import org.arcadia.arc_quest.quest.registry.CollectionEntryRegistry;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.arcadia.arc_quest.quest.spec.compile.QuestSpecCompiler;
import org.arcadia.arc_quest.quest.spec.validate.QuestSpecValidator;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CollectionSheetCompatibilityTest {
    @BeforeAll static void setup() { MinecraftRegistryTestBootstrap.initialize(); assertNotNull(QuestCategory.ADVENTURE); assertNotNull(ObjectiveType.CUSTOM); }

    @Test void modernJsonRoundTripsGuideImagesBindingPoliciesAndStableResearchIds() {
        var source = valid();
        assertFalse(new QuestSpecValidator().validate(source).hasErrors());
        var roundTrip = QuestSpecJsonReader.read(QuestSpecJsonWriter.write(source));
        var server = new QuestSpecCompiler().compile(roundTrip);
        var client = QuestSpecCompiler.compileClientPresentation(roundTrip, Map.of());
        for (var definition : java.util.List.of(server, client)) {
            assertTrue(definition.hasCollectionSheets());
            var entry = definition.getCollectionConfig().getEntry(ResourceLocation.parse("example:zombie"));
            assertNotNull(entry);
            assertEquals("study", entry.getResearchObjectives().get(0).getObjectiveId());
            assertEquals("portrait", entry.getContent().get(0).blockId());
            assertEquals(CollectionMediaFit.CONTAIN, entry.getContent().get(0).fit());
            assertTrue(entry.getContent().get(0).zoomable());
            assertEquals(ResourceLocation.parse("example:textures/codex/zombie.png"), entry.getContent().get(0).media().getTexture());
            assertEquals(1, definition.getPhase("survey").getCollectionSheet().getRequiredCount());
        }
    }

    @Test void invalidResearchReferenceQuotaAndImplicitObjectiveIdsAreDiagnosed() {
        var badResearch = valid();
        badResearch.phases.get(0).collectionSheet.bindings.get(0).recordRequirements.get(0).stepId = "missing";
        assertTrue(new QuestSpecValidator().validate(badResearch).hasErrors());
        var badQuota = valid(); badQuota.phases.get(0).collectionSheet.completionPolicy = "QUOTA";
        badQuota.phases.get(0).collectionSheet.requiredCount = 2;
        assertTrue(new QuestSpecValidator().validate(badQuota).hasErrors());
        var implicit = valid(); implicit.collectionConfig.entries.get(0).researchObjectives.get(0).id = "";
        assertTrue(new QuestSpecValidator().validate(implicit).hasErrors());
    }

    @Test void sharedDefinitionCanBeReusedButConflictingDefinitionIsRejected() {
        var id = ResourceLocation.parse("example:shared_definition_test");
        var one = CollectionEntryBuilder.create(id).category("mobs").displayName("Shared").build();
        var equivalent = CollectionEntryBuilder.create(id).category("mobs").displayName("Shared").build();
        CollectionEntryRegistry.register(one); CollectionEntryRegistry.register(equivalent);
        assertSame(one, CollectionEntryRegistry.getServerEntry(id));
        assertThrows(IllegalArgumentException.class, () -> CollectionEntryRegistry.register(
                CollectionEntryBuilder.create(id).category("mobs").displayName("Conflict").build()));
        assertSame(one, CollectionEntryRegistry.getServerEntry(id));
    }

    @Test void sharedInlineConditionsCompareTheirCanonicalSourceInsteadOfNewLambdaIdentity() {
        var one = valid();
        var condition = new org.arcadia.arc_quest.condition.ConditionSpec();
        condition.condition = "arc_quest:has_flag"; condition.flag = "survey_allowed";
        one.collectionConfig.entries.get(0).recordConditions.add(condition);
        var two = QuestSpecJsonReader.read(QuestSpecJsonWriter.write(one));
        var left = new QuestSpecCompiler().compile(one).getCollectionConfig().getEntries().get(0);
        var right = new QuestSpecCompiler().compile(two).getCollectionConfig().getEntries().get(0);
        assertTrue(CollectionEntryRegistry.equivalent(left, right));
    }

    @Test void allThreeSelfContainedJsonDemoQuestsValidateAndCompile() throws java.io.IOException {
        var directory = java.nio.file.Path.of(System.getProperty("arcq.test.projectDir", "."))
                .resolve("docs/examples/collection/collection-demo-pack/data/arc_quest_examples/arc_quest/quests");
        for (String name : java.util.List.of("field_compendium_demo", "renewable_survey_demo", "parallel_expedition_demo")) {
            var spec = QuestSpecJsonReader.read(java.nio.file.Files.readString(directory.resolve(name + ".json")));
            var report = new QuestSpecValidator().validate(spec);
            assertFalse(report.hasErrors(), () -> report.getIssues().toString());
            var definition = new QuestSpecCompiler().compile(spec);
            assertTrue(definition.hasCollectionSheets());
            assertEquals(QuestCategory.COLLECTION, definition.getCategory());
            var config = definition.getCollectionConfig();
            if (name.equals("field_compendium_demo")) {
                assertEquals(1, config.getQuestRewardNodes().size());
                assertEquals(EntryRewardGrantMode.AUTO, config.getQuestRewardNodes().get(0).getGrantMode());
                assertEquals(definition.getId().toString(), config.getQuestRewardNodes().get(0).getOwnerId());
                var living = config.getCategories().stream().filter(category -> category.getCategoryId().equals("living")).findFirst().orElseThrow();
                assertEquals(1, living.getRewardNodes().size());
                assertEquals(EntryRewardGrantMode.MANUAL, living.getRewardNodes().get(0).getGrantMode());
                assertEquals("living", living.getRewardNodes().get(0).getOwnerId());
            } else {
                assertTrue(config.getQuestRewardNodes().isEmpty());
                assertTrue(config.getCategories().stream().allMatch(category -> category.getRewardNodes().isEmpty()));
            }
        }
    }

    @Test void clientDisclosureSnapshotCannotReplaceTheIntegratedServersDefinition() {
        var source = valid(); source.id = "example:side_isolation_test";
        var server = new QuestSpecCompiler().compile(source);
        var redacted = valid(); redacted.id = source.id; redacted.collectionConfig.entries.get(0).displayName.value = "Anonymous";
        var client = QuestSpecCompiler.compileClientPresentation(redacted, Map.of());
        var previous = QuestRegistry.getDatapackSnapshot();
        try {
            var merged = new java.util.LinkedHashMap<>(previous); merged.put(server.getId(), server);
            QuestRegistry.replaceDatapackSnapshot(merged);
            QuestRegistry.replaceClientPresentationSnapshot(Map.of(client.getId(), client));
            assertSame(server, QuestRegistry.getServerDefinition(server.getId()));
            assertEquals("僵尸", QuestRegistry.getServerDefinition(server.getId()).getCollectionConfig().getEntries().get(0).getDisplayName().getString());
        } finally {
            QuestRegistry.clearClientPresentationSnapshot();
            QuestRegistry.replaceDatapackSnapshot(previous);
        }
    }

    private static QuestSpec valid() {
        return QuestSpecJsonReader.read("""
                {
                  "id":"example:collection", "category":"arc_quest:adventure", "mode":"COLLECTION", "initialPhaseId":"survey",
                  "displayName":{"mode":"literal","value":"调查"},
                  "collectionConfig":{
                    "categories":[{"categoryId":"mobs","displayName":{"mode":"literal","value":"生物"}}],
                    "entries":[{
                      "entryId":"example:zombie","categoryId":"mobs","subjectKind":"ENTITY","subjectId":"minecraft:zombie",
                      "displayName":{"mode":"literal","value":"僵尸"},
                      "researchObjectives":[{"id":"study","type":"arc_quest:custom","targetId":"example:study","requiredCount":3}],
                      "content":[{"blockId":"portrait","text":{"mode":"literal","value":"调查档案"},
                        "media":{"type":"image","texture":"example:textures/codex/zombie.png","width":240,"height":120},
                        "fit":"CONTAIN","zoomable":true,"reveal":"RESEARCH_STEP","revealStepId":"study"}]
                    }]
                  },
                  "phases":[{"phaseId":"survey","objectives":[],"collectionSheet":{
                    "completionPolicy":"ALL","bindings":[{"bindingId":"zombie_research","entryId":"example:zombie",
                      "recordRequirements":[{"type":"RESEARCH_STEP","stepId":"study"}]}]
                  }}]
                }
                """);
    }
}
