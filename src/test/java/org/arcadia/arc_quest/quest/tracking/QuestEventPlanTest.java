package org.arcadia.arc_quest.quest.tracking;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QuestEventPlanTest {
    private static final ResourceLocation APPLE = ResourceLocation.parse("minecraft:apple");
    private static final ResourceLocation ENTRY = ResourceLocation.parse("example:apple");
    @BeforeAll static void setup() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void craftAndCollectFreezeTogetherSoTheSameOutputNeverReachesASubsequentPhase() {
        var definition = QuestBuilder.create("example:event_stages")
                .phase(PhaseBuilder.create("prepare").objective(ObjectiveBuilder.craft(Items.APPLE, 1)).thenGoTo("samples"))
                .phase(PhaseBuilder.create("samples").objective(ObjectiveBuilder.collect(Items.APPLE, 1))).build();
        var data = playerWithRun(definition, "prepare");
        var plan = freeze(data, definition, crafted());
        assertEquals(1, plan.size());
        assertEquals("prepare", plan.get(0).reference().phaseId());
        var run = data.getActiveQuest(definition.getId().toString());
        run.completePhase("prepare");
        run.activatePhase("samples", 1);
        assertEquals(1, plan.size(), "Recipient list remains immutable after phase activation");
        assertEquals(0, run.getObjectiveProgress("samples", 0));
        assertFalse(plan.get(0).stillEligible(data));
    }

    @Test void aCraftEventUpdatesBothKindsOfObjectivesInTheSameActivePhase() {
        var definition = QuestBuilder.create("example:event_same_phase")
                .phase(PhaseBuilder.create("survey")
                        .objective(ObjectiveBuilder.craft(Items.APPLE, 1))
                        .objective(ObjectiveBuilder.collect(Items.APPLE, 1))).build();
        var data = playerWithRun(definition, "survey");
        var plan = freeze(data, definition, crafted());
        assertEquals(List.of(0, 1), plan.stream().map(p -> p.reference().objIndex()).toList());
        assertTrue(plan.stream().allMatch(p -> p.stillEligible(data)));
    }

    @Test void runningDefinitionUsesItsOwnTargetIndexAfterTheLiveQuestChangesTargets() {
        var old = QuestBuilder.create("example:event_versions")
                .phase(PhaseBuilder.create("survey").objective(ObjectiveBuilder.collect(Items.APPLE, 2))).build();
        var changed = QuestBuilder.create("example:event_versions")
                .phase(PhaseBuilder.create("survey").objective(ObjectiveBuilder.collect(Items.CARROT, 2))).build();
        var data = playerWithRun(old, "survey");
        var apple = List.of(new QuestEventPlan.Signal(ObjectiveType.COLLECT, APPLE, 1, false));
        assertEquals(1, QuestEventPlan.freeze(data, apple, run -> old).size(),
                "The accepted version still recognizes its apple objective");
        assertTrue(QuestEventPlan.freeze(data, apple, run -> changed).isEmpty(),
                "The new version's different target cannot overwrite an accepted run's routing");
    }

    @Test void resetAndReacceptCannotUseRecipientsCapturedForTheEarlierRun() {
        var definition = QuestBuilder.create("example:event_identity")
                .phase(PhaseBuilder.create("survey").objective(ObjectiveBuilder.craft(Items.APPLE, 1))).build();
        var data = playerWithRun(definition, "survey");
        var plan = freeze(data, definition, crafted());
        data.removeActiveQuest(definition.getId().toString());
        data.addActiveQuest(new QuestRuntimeData(definition.getId().toString(), "survey", 1, 2, 3, 4));
        assertFalse(plan.get(0).stillEligible(data));
        assertEquals(0, data.getActiveQuest(definition.getId().toString()).getObjectiveProgress("survey", 0));
    }

    @Test void possessionRejectsAllAcquisitionSignalsAndCraftedOnlyRejectsPickupDiffs() {
        var definition = QuestBuilder.create("example:event_modes")
                .phase(PhaseBuilder.create("survey")
                        .objective(ObjectiveBuilder.collect(Items.APPLE, 2))
                        .objective(ObjectiveBuilder.possess(Items.APPLE, 2))
                        .objective(ObjectiveBuilder.collect(Items.APPLE, 2).collectMode(CollectMode.CRAFTED_ONLY))).build();
        var data = playerWithRun(definition, "survey");
        var acquired = List.of(new QuestEventPlan.Signal(ObjectiveType.COLLECT, APPLE, 1, false));
        var produced = List.of(new QuestEventPlan.Signal(ObjectiveType.COLLECT, APPLE, 1, true));
        assertEquals(List.of(0), freeze(data, definition, acquired).stream().map(p -> p.reference().objIndex()).toList());
        assertEquals(List.of(0, 2), freeze(data, definition, produced).stream().map(p -> p.reference().objIndex()).toList());
        var possession = definition.getPhase("survey").getObjectives().get(1);
        assertEquals(2, QuestEventManager.possessionCount(possession, Map.of(APPLE, 2)));
        assertEquals(1, QuestEventManager.possessionCount(possession, Map.of(APPLE, 1)));
        assertEquals(0, QuestEventManager.possessionCount(possession, Map.of()));
    }

    @Test void tagEventRoutingAndHoldingsUseFrozenMembersIncludingAnExplicitlyEmptySet() {
        var tag = ResourceLocation.parse("example:frozen_event_members");
        var definition = QuestBuilder.create("example:tag_freeze")
                .phase(PhaseBuilder.create("survey").objective(ObjectiveBuilder.collectTag(tag, 3))).build();
        var data = playerWithRun(definition, "survey");
        var run = data.getActiveQuest(definition.getId().toString());
        run.freezeItemTag(tag, List.of(APPLE));
        var signals = List.of(new QuestEventPlan.Signal(ObjectiveType.COLLECT, APPLE, 1, false));
        assertEquals(1, QuestEventPlan.freeze(data, signals, ignored -> definition).size());
        var objective = definition.getPhase("survey").getObjectives().get(0);
        assertEquals(2, QuestEventManager.possessionCount(objective, Map.of(APPLE, 2), run));
        var empty = new QuestRuntimeData(definition.getId().toString(), "survey", 1, 0, 0, 0);
        empty.freezeItemTag(tag, List.of());
        data.removeActiveQuest(definition.getId().toString()); data.addActiveQuest(empty);
        assertTrue(QuestEventPlan.freeze(data, signals, ignored -> definition).isEmpty());
        assertEquals(0, QuestEventManager.possessionCount(objective, Map.of(APPLE, 2), empty));
    }

    @Test void completedBindingsAndSettledSheetsFreezeBoundObjectivesButAllowAnIndependentReport() {
        var definition = QuestBuilder.create("example:event_sheet").mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create()
                        .category("field", "Field").entry(CollectionEntryBuilder.create(ENTRY).category("field")).build())
                .phase(PhaseBuilder.create("survey")
                        .objective(ObjectiveBuilder.collect(Items.APPLE, 1).id("sample"))
                        .objective(ObjectiveBuilder.collect(Items.APPLE, 1).id("report"))
                        .collectionSheet(CollectionSheetBuilder.create().binding(
                                EntryRequirementBuilder.create("apple", ENTRY).objective("sample")))).build();
        var data = playerWithRun(definition, "survey");
        var run = data.getActiveQuest(definition.getId().toString());
        var signals = List.of(new QuestEventPlan.Signal(ObjectiveType.COLLECT, APPLE, 1, false));
        var plan = freeze(data, definition, signals);
        assertEquals(2, plan.size());
        run.getOrCreateCollectionData().markBindingComplete("survey", "apple");
        assertFalse(plan.get(0).stillEligible(data));
        assertTrue(plan.get(1).stillEligible(data));
        assertEquals(List.of(1), freeze(data, definition, signals).stream().map(p -> p.reference().objIndex()).toList());
        run.getOrCreateCollectionData().markSheetSettled("survey");
        assertEquals(List.of(1), freeze(data, definition, signals).stream().map(p -> p.reference().objIndex()).toList());
    }

    private static List<QuestEventPlan.Signal> crafted() {
        return List.of(new QuestEventPlan.Signal(ObjectiveType.CRAFT, APPLE, 1, true),
                new QuestEventPlan.Signal(ObjectiveType.COLLECT, APPLE, 1, true));
    }

    private static List<QuestEventPlan.Recipient> freeze(ArcQuestPlayer data, QuestDefinition definition,
                                                       List<QuestEventPlan.Signal> signals) {
        var definitions = Map.of(definition.getId(), definition);
        return QuestEventPlan.freeze(data, signals, ObjectiveTypeIndex.build(definitions), definitions::get);
    }

    private static ArcQuestPlayer playerWithRun(QuestDefinition definition, String phase) {
        var data = new ArcQuestPlayer(UUID.randomUUID());
        data.addActiveQuest(new QuestRuntimeData(definition.getId().toString(), phase,
                definition.getPhase(phase).getObjectives().size(), 0, 0, 0));
        return data;
    }
}
