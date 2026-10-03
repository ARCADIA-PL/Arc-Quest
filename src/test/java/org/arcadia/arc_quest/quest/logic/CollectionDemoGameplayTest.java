package org.arcadia.arc_quest.quest.logic;

import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.data.*;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector;
import org.arcadia.arc_quest.quest.registry.*;
import org.arcadia.arc_quest.client.quest.tracking.CollectionTrackingFocusSelector;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CollectionDemoGameplayTest {
    @BeforeAll static void setup() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void eachDemoContainsOnlyItsBoundSubjectsAndNonemptyCategories() {
        var entries = CollectionFieldDemos.entries();
        for (var quest : List.of(CollectionFieldDemos.field(entries), CollectionFieldDemos.renewable(entries), CollectionFieldDemos.parallel(entries))) {
            var bound = quest.getPhaseIds().stream().flatMap(id -> quest.getPhase(id).getCollectionSheet().getBindings().stream())
                    .map(EntryRequirementBinding::getEntryId).collect(java.util.stream.Collectors.toSet());
            var configured = quest.getCollectionConfig().getEntries();
            assertEquals(bound, configured.stream().map(CollectionEntryDefinition::getEntryId).collect(java.util.stream.Collectors.toSet()));
            assertEquals(configured.stream().map(CollectionEntryDefinition::getCategoryId).collect(java.util.stream.Collectors.toSet()),
                    quest.getCollectionConfig().getCategories().stream().map(CollectionCategoryDefinition::getCategoryId).collect(java.util.stream.Collectors.toSet()));
        }
    }

    @Test void discoveredSubjectsStillHaveEightRealActionableHandbookInvestigations() {
        var quest = CollectionFieldDemos.field(CollectionFieldDemos.entries());
        var phase = quest.getPhase("survey");
        var records = new CollectionRecordState();
        quest.getCollectionConfig().getEntries().forEach(entry -> records.discover(entry.getEntryId()));
        var run = new QuestRuntimeData(quest.getId().toString(), "survey", phase.getObjectives().size(), 1, 2, 3);
        CollectionSheetService.initialize(quest, run, records);
        var projection = CollectionProgressProjector.project(quest, phase, run, records);
        assertEquals(8, projection.bindings().size()); assertEquals(0, projection.completed());
        for (var binding : projection.bindings()) {
            assertFalse(binding.complete());
            assertTrue(binding.requirements().stream().anyMatch(row -> row.objective() != null && !row.complete()));
            assertTrue(CollectionTrackingFocusSelector.canTrack(quest, run, "survey", binding.bindingId(), ignored -> projection));
        }
        assertEquals(ObjectiveType.CRAFT, phase.getObjectives().get(phase.getObjectiveIndex("bone_processing")).getType());
        assertEquals(ObjectiveType.OFFER, phase.getObjectives().get(phase.getObjectiveIndex("milk_sample")).getType());
    }

    @Test void completedCowAndBoneInvestigationsStopTrackingAndEarnUsefulArchiveFacts() {
        var quest = CollectionFieldDemos.field(CollectionFieldDemos.entries());
        var phase = quest.getPhase("survey");
        var data = new org.arcadia.arc_quest.questplayer.ArcQuestPlayer(java.util.UUID.randomUUID());
        var records = data.getCollectionRecords();
        records.discover(CollectionFieldDemos.COW); records.discover(CollectionFieldDemos.BONE);
        var run = new QuestRuntimeData(quest.getId().toString(), "survey", phase.getObjectives().size(), 1, 2, 3);
        data.addActiveQuest(run);
        CollectionSheetService.initialize(quest, run, records);
        for (String id : List.of("cow_contact", "milk_sample", "bone_processing")) {
            int index = phase.getObjectiveIndex(id);
            run.setObjectiveProgress("survey", index, phase.getObjectives().get(index).getRequiredCount());
        }
        CollectionSheetService.satisfied(quest, phase, run, records);
        CollectionEntryRewardService.updateBindings(null, data, quest, phase, run);
        var projection = CollectionProgressProjector.project(quest, phase, run, records);
        assertTrue(records.hasOutcome(CollectionFieldDemos.COW, "dairy"));
        assertTrue(records.hasOutcome(CollectionFieldDemos.BONE, "cultivation"));
        for (String id : List.of("cow", "bone")) {
            assertTrue(projection.binding(id).complete());
            assertFalse(CollectionTrackingFocusSelector.canTrack(quest, run, "survey", id, ignored -> projection));
        }
        assertEquals(2, projection.completed());
    }

    @Test void previousExecutableVersionRemainsExactForAllThreeQuestIds() {
        CollectionDemoDefinitionFactories.ensureRegistered();
        var store = new CollectionRunDefinitionStore();
        for (var quest : List.of(CollectionFieldDemos.field(CollectionFieldDemos.entries()),
                CollectionFieldDemos.renewable(CollectionFieldDemos.entries()), CollectionFieldDemos.parallel(CollectionFieldDemos.entries()))) {
            var hash = store.freezeCodeFactory(quest.getId(), CollectionDemoDefinitionFactories.PREVIOUS_VERSION);
            var saved = store.save(new net.minecraft.nbt.CompoundTag());
            var old = CollectionRunDefinitionStore.load(saved).resolve(hash, quest.getId());
            assertEquals(9, old.getCollectionConfig().getEntries().size());
            assertEquals(quest.getId().equals(CollectionFieldDemos.FIELD) ? 8
                    : quest.getId().equals(CollectionFieldDemos.RENEWABLE) ? 6 : 7,
                    quest.getCollectionConfig().getEntries().size());
            if (quest.getId().equals(CollectionFieldDemos.FIELD)) {
                assertTrue(old.getPhase("survey").getCollectionSheet().getBinding("cow").getObjectiveIds().isEmpty());
                assertFalse(quest.getPhase("survey").getCollectionSheet().getBinding("cow").getObjectiveIds().isEmpty());
            }
            if (quest.getId().equals(CollectionFieldDemos.RENEWABLE)) {
                assertEquals(ObjectiveType.KILL, old.getPhase("round").getObjectives().get(0).getType());
                assertEquals(ObjectiveType.OFFER, quest.getPhase("round").getObjectives().get(0).getType());
            }
        }
    }
}
