package org.arcadia.arc_quest.quest.logic;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.CollectionEntryBuilder;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.quest.data.CollectionRecordState;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

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

    @Test void emptyResearchMeansRecordedInsteadOfZeroOfZeroAndContentUnlocksByStableStep() {
        var records = new CollectionRecordState();
        var entry = CollectionEntryBuilder.create(ID).category("mobs").build();
        assertFalse(CollectionProgressProjector.researchComplete(entry, records));
        records.discover(ID); assertTrue(CollectionProgressProjector.researchComplete(entry, records));
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
