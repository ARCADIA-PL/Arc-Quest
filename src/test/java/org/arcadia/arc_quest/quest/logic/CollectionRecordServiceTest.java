package org.arcadia.arc_quest.quest.logic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.CollectionRecordState;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CollectionRecordServiceTest {
    private static final ResourceLocation ID = ResourceLocation.parse("example:zombie");
    @BeforeAll static void setup() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void knowledgeAccumulatesWithoutAnyAcceptedQuestAndCountersAreCapped() {
        var entry = entry(false); var records = new CollectionRecordState();
        CollectionRecordService.applyMatchedRules(records, rules(entry), 1, e -> true);
        assertTrue(records.isDiscovered(ID));
        assertEquals(1, records.getProgress(ID, CollectionProgressProjector.researchKey("kill")));
        CollectionRecordService.applyMatchedRules(records, rules(entry), Integer.MAX_VALUE, e -> true);
        assertEquals(5, records.getProgress(ID, CollectionProgressProjector.researchKey("kill")));
        long revision = records.getRevision();
        assertTrue(CollectionRecordService.applyMatchedRules(records, rules(entry), 1, e -> true).isEmpty());
        assertEquals(revision, records.getRevision());
    }

    @Test void sameRealEventDiscoversAndResearchesExactlyOnce() {
        var entry = entry(true); var records = new CollectionRecordState();
        // Reverse the incoming index order to prove discovery ordering is explicit.
        var rules = List.of(new CollectionRecordService.RuleRef(entry, entry.getResearchObjectives().get(0), CollectionRecordService.Scope.RESEARCH),
                new CollectionRecordService.RuleRef(entry, entry.getDiscoveryObjectives().get(0), CollectionRecordService.Scope.DISCOVERY));
        CollectionRecordService.applyMatchedRules(records, rules, 1, e -> true);
        assertTrue(records.isDiscovered(ID));
        assertEquals(1, records.getProgress(ID, CollectionProgressProjector.researchKey("kill")));
    }

    @Test void recordQualificationCanDenyEventsRegardlessOfPublicVisibility() {
        var entry = entry(false); var records = new CollectionRecordState();
        assertEquals(VisibilityMode.VISIBLE_BY_DEFAULT, entry.getVisibilityMode());
        assertTrue(CollectionRecordService.applyMatchedRules(records, rules(entry), 3, e -> false).isEmpty());
        assertFalse(records.isDiscovered(ID));
        assertEquals(0, records.getProgress(ID, CollectionProgressProjector.researchKey("kill")));
        CollectionRecordService.applyMatchedRules(records, rules(entry), 1, e -> true);
        assertTrue(records.isDiscovered(ID));
    }

    @Test void researchAfterDiscoveryBlocksPriorActionsButTheDefaultCanAccumulateThem() {
        for (boolean afterDiscovery : List.of(false, true)) {
            var entry = entry(afterDiscovery); var records = new CollectionRecordState();
            var researchOnly = List.of(new CollectionRecordService.RuleRef(entry, entry.getResearchObjectives().get(0), CollectionRecordService.Scope.RESEARCH));
            CollectionRecordService.applyMatchedRules(records, researchOnly, 2, e -> true);
            assertEquals(afterDiscovery ? 0 : 2, records.getProgress(ID, CollectionProgressProjector.researchKey("kill")));
            assertFalse(records.isDiscovered(ID));
        }
    }

    @Test void discoveredEntriesWithoutResearchDoNotInventDeepInvestigationOrZeroOfZeroProgress() {
        var records = new CollectionRecordState();
        var entry = CollectionEntryBuilder.create(ID).category("mobs").build();
        assertFalse(CollectionProgressProjector.researchComplete(entry, records));
        records.discover(ID); assertFalse(CollectionProgressProjector.researchComplete(entry, records));
        assertTrue(records.isDiscovered(ID), "Basic discovery is independent of a deeper investigation");
        assertTrue(CollectionProgressProjector.researchProgress(entry, records).isEmpty(), "There is no synthetic 0/0 investigation row");
        var researched = entry(false);
        var block = new CollectionContentBlock("anatomy", QuestText.literal("Knowledge"), null, QuestText.literal(""),
                CollectionMediaFit.CONTAIN, true, CollectionContentReveal.RESEARCH_STEP, "kill");
        assertFalse(CollectionProgressProjector.contentRevealed(researched, block, records));
        records.increment(ID, CollectionProgressProjector.researchKey("kill"), 5, 5);
        assertTrue(CollectionProgressProjector.contentRevealed(researched, block, records));
    }

    @Test void optionalLongTermResearchDoesNotBlockResearchComplete() {
        var entry = CollectionEntryBuilder.create(ID).category("mobs")
                .research(ObjectiveBuilder.custom(ID, 2).id("required"))
                .research(ObjectiveBuilder.custom(ID, 99).id("optional").optional()).build();
        var records = new CollectionRecordState(); records.discover(ID);
        records.increment(ID, CollectionProgressProjector.researchKey("required"), 2, 2);
        assertTrue(CollectionProgressProjector.researchComplete(entry, records));
        assertEquals(0, records.getProgress(ID, CollectionProgressProjector.researchKey("optional")));
    }

    @Test void publicPermanentProgressIsReadOnlyAndNeverIncludesHiddenRulesOrUndiscoveredPrivateEntries() {
        var entry = CollectionEntryBuilder.create(ID).category("mobs")
                .discover(ObjectiveBuilder.custom(ID, 3).id("discover").display("Inspect specimen"))
                .research(ObjectiveBuilder.custom(ID, 5).id("study").display("Study specimen"))
                .research(ObjectiveBuilder.custom(ID, 2).id("extra").optional())
                .research(ObjectiveBuilder.custom(ID, 99).id("secret").hidden()).build();
        var records = new CollectionRecordState();
        records.increment(ID, CollectionProgressProjector.discoveryKey("discover"), 2, 3);
        records.increment(ID, CollectionProgressProjector.researchKey("study"), 3, 5);
        var before = records.serializeNBT();
        var discovery = CollectionProgressProjector.discoveryProgress(entry, records);
        assertEquals(1, discovery.size()); assertEquals(2, discovery.get(0).current()); assertEquals(3, discovery.get(0).target());
        assertFalse(discovery.get(0).complete()); assertEquals("Inspect specimen", discovery.get(0).label().getString());
        var research = CollectionProgressProjector.researchProgress(entry, records.getRecord(ID));
        assertEquals(2, research.size()); assertEquals(3, research.get(0).current()); assertEquals(5, research.get(0).target());
        assertSame(entry.getResearchObjectives().get(0), research.get(0).objective());
        assertEquals(-1, research.get(0).objectiveIndex()); assertTrue(research.get(1).optional());
        assertEquals(before, records.serializeNBT());
        assertEquals(0, CollectionProgressProjector.researchProgress(entry, (org.arcadia.arc_quest.quest.data.CollectionEntryRecord) null).get(0).current());
        var privateEntry = CollectionEntryBuilder.create(ID).category("mobs")
                .visibility(VisibilityMode.HIDDEN_BY_DEFAULT, HiddenPresentationMode.PLACEHOLDER)
                .discover(ObjectiveBuilder.custom(ID, 1).id("discover"))
                .research(ObjectiveBuilder.custom(ID, 5).id("study")).build();
        assertTrue(CollectionProgressProjector.discoveryProgress(privateEntry, records).isEmpty());
        assertTrue(CollectionProgressProjector.researchProgress(privateEntry, records).isEmpty());
        records.discover(ID);
        assertEquals(1, CollectionProgressProjector.researchProgress(privateEntry, records).size());
        assertEquals(3, CollectionProgressProjector.researchProgress(privateEntry, records).get(0).current());
    }

    @Test void defaultPublicIdentityDoesNotInventDiscoveryOrPublishDiscoveredContent() {
        var entry = CollectionEntryBuilder.create(ID).category("mobs")
                .discover(ObjectiveBuilder.custom(ID, 1).id("discover"))
                .research(ObjectiveBuilder.custom(ID, 5).id("study"))
                .text("notes", "Observation notes").build();
        var records = new CollectionRecordState();
        assertEquals(VisibilityMode.VISIBLE_BY_DEFAULT, entry.getVisibilityMode());
        assertFalse(entry.isResearchAfterDiscovery());
        assertFalse(records.isDiscovered(ID)); assertFalse(CollectionProgressProjector.researchComplete(entry, records));
        assertEquals(CollectionContentReveal.DISCOVERED, entry.getContent().get(0).reveal());
        assertFalse(CollectionProgressProjector.contentRevealed(entry, entry.getContent().get(0), records));
        assertEquals(0, CollectionProgressProjector.discoveryProgress(entry, records).get(0).current());
        records.discover(ID);
        assertTrue(CollectionProgressProjector.contentRevealed(entry, entry.getContent().get(0), records));
        assertFalse(CollectionProgressProjector.researchComplete(entry, records));
    }

    @Test void resetBlocksPreexistingInventoryImportsAcrossReloadButNewEventsCountNormally() {
        var entry = CollectionEntryBuilder.create(ID).category("materials").item(Items.COAL)
                .discover(ObjectiveBuilder.collect(Items.COAL, 3).id("sample"))
                .research(ObjectiveBuilder.collect(Items.COAL, 5).id("study")).build();
        var untouched = CollectionEntryBuilder.create(ResourceLocation.parse("example:untouched_inventory_entry"))
                .category("materials").item(Items.COAL).discover(ObjectiveBuilder.collect(Items.COAL, 1).id("sample")).build();
        var rules = rules(entry); var records = new CollectionRecordState();
        var held = new LinkedHashMap<CollectionRecordService.RuleRef, Integer>();
        held.put(rules.get(0), 8);
        assertEquals(Set.of(ID), CollectionRecordService.importInventoryDiscovery(records, held, ignored -> true));
        assertTrue(records.isDiscovered(ID)); assertEquals(0, records.getProgress(ID, CollectionProgressProjector.researchKey("study")));
        records.resetQuest("example:inventory_reset", Set.of(ID));
        var restored = new CollectionRecordState(); restored.readSnapshot(records.serializeNBT());
        held.put(new CollectionRecordService.RuleRef(untouched, untouched.getDiscoveryObjectives().get(0), CollectionRecordService.Scope.DISCOVERY), 8);
        assertEquals(Set.of(untouched.getEntryId()), CollectionRecordService.importInventoryDiscovery(restored, held, ignored -> true));
        assertFalse(restored.isDiscovered(ID)); assertEquals(0, restored.getProgress(ID, CollectionProgressProjector.discoveryKey("sample")));
        assertTrue(restored.isDiscovered(untouched.getEntryId()));
        assertEquals(Set.of(ID), CollectionRecordService.applyMatchedRules(restored, rules, 1, ignored -> true));
        assertFalse(restored.isDiscovered(ID)); assertEquals(1, restored.getProgress(ID, CollectionProgressProjector.discoveryKey("sample")));
        assertEquals(1, restored.getProgress(ID, CollectionProgressProjector.researchKey("study")));
        assertTrue(CollectionRecordService.importInventoryDiscovery(restored, held, ignored -> true).isEmpty());
        assertEquals(1, restored.getProgress(ID, CollectionProgressProjector.discoveryKey("sample")), "A later login must not complete a partially rebuilt entry");
        CollectionRecordService.applyMatchedRules(restored, rules, 2, ignored -> true);
        assertTrue(restored.isDiscovered(ID)); assertEquals(3, restored.getProgress(ID, CollectionProgressProjector.researchKey("study")));
    }

    @Test void registeredStringResetMapsLegacySubjectsAndPreventsStaleLegacyProgressFromBeingReimported() {
        var entry = CollectionEntryBuilder.create(ID).category("materials").item(Items.COAL)
                .discover(ObjectiveBuilder.collect(Items.COAL, 1).id("sample"))
                .research(ObjectiveBuilder.collect(Items.COAL, 5).id("study")).build();
        var modern = QuestBuilder.create("example:legacy_reset_modern").mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("materials", "Materials").entry(entry).build())
                .phase(PhaseBuilder.create("survey").collectionSheet(CollectionSheetBuilder.create()
                        .binding(EntryRequirementBuilder.create("sample", ID).discovered()))).build();
        var legacy = QuestBuilder.create("example:legacy_reset_old").mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("materials", "Materials").build())
                .phase(PhaseBuilder.create("old_entry").objective(ObjectiveBuilder.collect(Items.COAL, 5))
                        .collectionEntryConfig(new CollectionEntryConfig("materials", null, null, List.of(), null,
                                5, false, false, 0, null, List.of(), 0, true))).build();
        var previous = QuestRegistry.getDatapackSnapshot(); var installed = new LinkedHashMap<>(previous);
        installed.put(modern.getId(), modern); installed.put(legacy.getId(), legacy);
        try {
            QuestRegistry.replaceDatapackSnapshot(installed);
            var data = new ArcQuestPlayer(UUID.randomUUID()); var records = data.getCollectionRecords();
            var old = new QuestRuntimeData(legacy.getId().toString(), "old_entry", 1, 0, 0, 0);
            old.getOrCreateCollectionData().markDiscovered("old_entry"); old.getCollectionData().setEntryCount("old_entry", 5, 5);
            data.addActiveQuest(old); LegacyCollectionMigration.migrate(data);
            assertTrue(records.isDiscovered(ID)); assertEquals(5, records.getProgress(ID, CollectionProgressProjector.researchKey("study")));
            assertTrue(records.isLegacyMigrated(legacy.getId() + "/old_entry"));
            data.resetQuest(legacy.getId().toString());
            assertNull(data.getActiveQuest(legacy.getId().toString()));
            assertFalse(records.isLegacyMigrated(legacy.getId() + "/old_entry")); assertTrue(records.isEntryReset(ID));
            var restored = new ArcQuestPlayer(UUID.randomUUID()); restored.deserializeNBT(data.serializeNBT());
            // A surviving old task snapshot cannot refill an explicitly reset shared entry.
            restored.addActiveQuest(QuestRuntimeData.deserializeNBT(old.serializeNBT())); LegacyCollectionMigration.migrate(restored);
            assertFalse(restored.getCollectionRecords().isDiscovered(ID));
            assertEquals(0, restored.getCollectionRecords().getProgress(ID, CollectionProgressProjector.researchKey("study")));
            CollectionRecordService.applyMatchedRules(restored.getCollectionRecords(), rules(entry), 1, ignored -> true);
            assertTrue(restored.getCollectionRecords().isDiscovered(ID));
            assertEquals(1, restored.getCollectionRecords().getProgress(ID, CollectionProgressProjector.researchKey("study")));
        } finally { QuestRegistry.replaceDatapackSnapshot(previous); }
    }

    private static CollectionEntryDefinition entry(boolean after) {
        return CollectionEntryBuilder.create(ID).category("mobs").researchAfterDiscovery(after)
                .discover(ObjectiveBuilder.custom(ID, 1).id("discover"))
                .research(ObjectiveBuilder.custom(ID, 5).id("kill")).build();
    }
    private static List<CollectionRecordService.RuleRef> rules(CollectionEntryDefinition entry) {
        return List.of(new CollectionRecordService.RuleRef(entry, entry.getDiscoveryObjectives().get(0), CollectionRecordService.Scope.DISCOVERY),
                new CollectionRecordService.RuleRef(entry, entry.getResearchObjectives().get(0), CollectionRecordService.Scope.RESEARCH));
    }
}
