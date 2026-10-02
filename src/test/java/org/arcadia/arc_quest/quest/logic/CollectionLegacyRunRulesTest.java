package org.arcadia.arc_quest.quest.logic;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.CollectionRecordState;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CollectionLegacyRunRulesTest {
    private static final ResourceLocation ENTRY = ResourceLocation.parse("example:legacy_subject");
    @BeforeAll static void setup() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void onlyAnActiveLegacyVersionReceivesItsResearchAndDuplicateOldRunsCountOneRealEvent() {
        var old = definition(CollectionEntryBuilder.create(ENTRY).category("field")
                .discover(ObjectiveBuilder.custom(ENTRY, 1).id("discover"))
                .research(ObjectiveBuilder.custom(ENTRY, 5).id("study")).build());
        var modern = definition(CollectionEntryBuilder.create(ENTRY).category("field")
                .discover(ObjectiveBuilder.custom(ENTRY, 1).id("discover")).outcome("study", "Study").build());
        var run = runtime(old);
        var matches = CollectionRecordService.legacyRulesFor(run, old, ObjectiveType.CUSTOM, ENTRY);
        assertEquals(2, matches.size());
        assertTrue(CollectionRecordService.legacyRulesFor(run, modern, ObjectiveType.CUSTOM, ENTRY).isEmpty());
        var duplicated = new ArrayList<>(matches); duplicated.addAll(matches);
        var records = new CollectionRecordState();
        CollectionRecordService.applyMatchedRules(records, duplicated, 1, entry -> true);
        assertTrue(records.isDiscovered(ENTRY));
        assertEquals(1, records.getProgress(ENTRY, CollectionProgressProjector.researchKey("study")));
        assertFalse(records.hasOutcome(ENTRY, "study"), "An old counting event cannot directly award a new investigation outcome");
        CollectionRecordService.applyMatchedRules(records, duplicated, Integer.MAX_VALUE, entry -> true);
        assertEquals(5, records.getProgress(ENTRY, CollectionProgressProjector.researchKey("study")));
        for (QuestState terminal : List.of(QuestState.COMPLETED, QuestState.FAILED)) {
            run.setState(terminal);
            assertTrue(CollectionRecordService.legacyRulesFor(run, old, ObjectiveType.CUSTOM, ENTRY).isEmpty(),
                    "Research compatibility listeners end with the accepted old run");
        }
    }

    @Test void aSmallerLegacyThresholdCannotClampAwayAnotherVersionsProgress() {
        var shorter = legacyEntry(3); var longer = legacyEntry(5);
        var records = new CollectionRecordState(); records.discover(ENTRY);
        var rows = List.of(new CollectionRecordService.RuleRef(longer, longer.getResearchObjectives().get(0), CollectionRecordService.Scope.RESEARCH),
                new CollectionRecordService.RuleRef(shorter, shorter.getResearchObjectives().get(0), CollectionRecordService.Scope.RESEARCH));
        CollectionRecordService.applyMatchedRules(records, rows, 4, entry -> true);
        assertEquals(4, records.getProgress(ENTRY, CollectionProgressProjector.researchKey("study")));
        CollectionRecordService.applyMatchedRules(records, rows, 1, entry -> true);
        assertEquals(5, records.getProgress(ENTRY, CollectionProgressProjector.researchKey("study")));
        CollectionRecordService.applyMatchedRules(records, List.of(rows.get(1)), 1, entry -> true);
        assertEquals(5, records.getProgress(ENTRY, CollectionProgressProjector.researchKey("study")),
                "Finishing the longer run cannot let a surviving shorter rule reduce recorded progress");
    }

    @Test void pinnedResearchMatchesItsFrozenTagEvenWhenTheLiveTagHasNoMembers() {
        var tag = ResourceLocation.parse("example:historical_samples");
        var entry = CollectionEntryBuilder.create(ENTRY).category("field")
                .research(ObjectiveBuilder.collectTag(tag, 3).id("study")).build();
        var definition = definition(entry); var run = runtime(definition);
        var apple = ResourceLocation.parse("minecraft:apple");
        run.freezeItemTag(tag, List.of(apple));
        assertFalse(ObjectiveItemResolver.matches(entry.getResearchObjectives().get(0), apple));
        assertEquals(1, CollectionRecordService.legacyRulesFor(run, definition, ObjectiveType.COLLECT, apple).size());
        assertTrue(CollectionRecordService.legacyRulesFor(run, definition, ObjectiveType.COLLECT,
                ResourceLocation.parse("minecraft:carrot")).isEmpty());
        var restored = QuestRuntimeData.deserializeNBT(run.serializeNBT());
        assertEquals(1, CollectionRecordService.legacyRulesFor(restored, definition, ObjectiveType.COLLECT, apple).size());
    }

    @Test void aLockedHigherThresholdCannotSuppressAnEligibleOlderVersionsResearchEvent() {
        var open = CollectionEntryBuilder.create(ENTRY).category("field")
                .research(ObjectiveBuilder.custom(ENTRY, 3).id("study")).build();
        var locked = CollectionEntryBuilder.create(ENTRY).category("field").researchAfterDiscovery(true)
                .research(ObjectiveBuilder.custom(ENTRY, 5).id("study")).build();
        var records = new CollectionRecordState();
        CollectionRecordService.applyMatchedRules(records, List.of(
                new CollectionRecordService.RuleRef(open, open.getResearchObjectives().get(0), CollectionRecordService.Scope.RESEARCH),
                new CollectionRecordService.RuleRef(locked, locked.getResearchObjectives().get(0), CollectionRecordService.Scope.RESEARCH)), 1, entry -> true);
        assertFalse(records.isDiscovered(ENTRY));
        assertEquals(1, records.getProgress(ENTRY, CollectionProgressProjector.researchKey("study")));
    }

    @Test void aFrozenPermanentEventCannotCrossAResetOrAPlayerDataReplacement() {
        var entry = legacyEntry(5);
        var data = new ArcQuestPlayer(UUID.randomUUID());
        var rule = new CollectionRecordService.RuleRef(entry, entry.getResearchObjectives().get(0), CollectionRecordService.Scope.RESEARCH);
        var event = CollectionRecordService.freezeRules(data, List.of(rule));
        data.getCollectionRecords().discover(ResourceLocation.parse("example:unrelated"));
        assertEquals(List.of(rule), event.currentRules(data), "An unrelated fact must not invalidate the frozen event");
        assertTrue(event.currentRules(new ArcQuestPlayer(data.getOwnerUuid())).isEmpty(),
                "Replacing player data cannot transfer an old event to the new owner");
        data.getCollectionRecords().resetQuest("example:source", Set.of(ENTRY));
        assertTrue(event.currentRules(data).isEmpty(), "Reset must invalidate every remaining signal of the old event");
        assertEquals(List.of(rule), CollectionRecordService.freezeRules(data, List.of(rule)).currentRules(data),
                "A new genuine post-reset event must still be able to record progress");
    }

    private static CollectionEntryDefinition legacyEntry(int count) {
        return CollectionEntryBuilder.create(ENTRY).category("field").research(ObjectiveBuilder.custom(ENTRY, count).id("study")).build();
    }
    private static QuestDefinition definition(CollectionEntryDefinition entry) {
        return QuestBuilder.create("example:legacy_rules").mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("field", "Field").entry(entry).build())
                .phase(PhaseBuilder.create("survey").collectionSheet(CollectionSheetBuilder.create()
                        .binding(EntryRequirementBuilder.create("subject", ENTRY).discovered()))).build();
    }
    private static QuestRuntimeData runtime(QuestDefinition definition) {
        return new QuestRuntimeData(definition.getId().toString(), "survey", 0, 0, 0, 0);
    }
}
