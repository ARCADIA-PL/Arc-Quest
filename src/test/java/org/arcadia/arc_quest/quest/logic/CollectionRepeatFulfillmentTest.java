package org.arcadia.arc_quest.quest.logic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.data.sync.CollectionDefinitionSpecExporter;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.reward.ItemReward;
import org.arcadia.arc_quest.quest.spec.compile.QuestSpecCompiler;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonReader;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonWriter;
import org.arcadia.arc_quest.quest.spec.validate.QuestSpecValidator;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CollectionRepeatFulfillmentTest {
    private static final ResourceLocation ENTRY = ResourceLocation.parse("example:fulfillment");
    @BeforeAll static void setup() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void paidRepeatableHoldingsInteractionCraftingAndLegacyAcquisitionNeedExplicitFulfillment() {
        for (ObjectiveBuilder action : java.util.List.of(ObjectiveBuilder.possess(Items.APPLE, 1),
                ObjectiveBuilder.interact(ENTRY), ObjectiveBuilder.craft(Items.APPLE, 1), ObjectiveBuilder.collect(Items.APPLE, 1),
                ObjectiveBuilder.reachLocation(ENTRY, 0, 64, 0, 4))) {
            assertThrows(IllegalArgumentException.class, () -> quest(action, 0).build());
        }
        assertDoesNotThrow(() -> quest(ObjectiveBuilder.offer(Items.APPLE, 1), 0).build());
        assertDoesNotThrow(() -> quest(ObjectiveBuilder.possess(Items.APPLE, 1), 1200).build());
    }

    @Test void abandoningAndReloadingCannotRemoveTheAcceptanceCooldownButAdministrativeResetCan() {
        var quest = quest(ObjectiveBuilder.possess(Items.APPLE, 1), 1200).build();
        var data = new ArcQuestPlayer(UUID.randomUUID());
        assertEquals(0, CollectionAcceptanceRules.cooldownRemaining(quest, data, 0));
        data.recordCollectionAcceptance(quest.getId().toString(), 100);
        data.addActiveQuest(new QuestRuntimeData(quest.getId().toString(), "survey", 1, 0, 0, 0));
        data.removeActiveQuest(quest.getId().toString());
        assertEquals(1100, CollectionAcceptanceRules.cooldownRemaining(quest, data, 200));
        data.deserializeNBT(data.serializeNBT());
        assertEquals(1100, CollectionAcceptanceRules.cooldownRemaining(quest, data, 200));
        assertEquals(0, CollectionAcceptanceRules.cooldownRemaining(quest, data, 1300));
        data.resetQuest(quest); assertEquals(0, CollectionAcceptanceRules.cooldownRemaining(quest, data, 200));
        data.recordCollectionAcceptance(quest.getId().toString(), 100);
        data.clearAllData(); assertEquals(0, CollectionAcceptanceRules.cooldownRemaining(quest, data, 200));
    }

    @Test void cooldownSurvivesJsonAndTheValidatorRejectsAnUnboundedPaidLoop() {
        var quest = quest(ObjectiveBuilder.possess(Items.APPLE, 1), 1200).build();
        var spec = CollectionDefinitionSpecExporter.quest(quest, null);
        assertEquals(1200, spec.collectionConfig.repeatCooldownTicks);
        var restored = QuestSpecJsonReader.read(QuestSpecJsonWriter.write(spec));
        assertFalse(new QuestSpecValidator().validate(restored).hasErrors());
        assertEquals(1200, new QuestSpecCompiler().compile(restored).getCollectionConfig().getRepeatCooldownTicks());
        restored.collectionConfig.repeatCooldownTicks = 0;
        assertTrue(new QuestSpecValidator().validate(restored).getIssues().stream()
                .anyMatch(issue -> issue.path.equals("phases[0].collectionSheet.bindings[0]")));
    }

    @Test void oneConsumedObjectiveCannotPayTwoInvestigationsButSeparateQuantitiesCan() {
        var phase = PhaseBuilder.create("survey").objective(ObjectiveBuilder.offer(Items.APPLE, 1).id("sample"))
                .collectionSheet(CollectionSheetBuilder.create()
                        .binding(EntryRequirementBuilder.create("one", ENTRY).objective("sample"))
                        .binding(EntryRequirementBuilder.create("two", ENTRY).objective("sample")));
        assertThrows(IllegalArgumentException.class, () -> base(0).phase(phase).build());
        var independent = PhaseBuilder.create("survey")
                .objective(ObjectiveBuilder.offer(Items.APPLE, 1).id("first"))
                .objective(ObjectiveBuilder.offer(Items.APPLE, 1).id("second"))
                .collectionSheet(CollectionSheetBuilder.create()
                        .binding(EntryRequirementBuilder.create("one", ENTRY).objective("first"))
                        .binding(EntryRequirementBuilder.create("two", ENTRY).objective("second")));
        assertDoesNotThrow(() -> base(0).phase(independent).build());
    }

    @Test void anAnyRequirementCannotBypassPaidFreshFulfillmentUsingAPermanentRecord() {
        var phase = PhaseBuilder.create("survey").reward(new ItemReward(Items.EMERALD, 1))
                .objective(ObjectiveBuilder.offer(Items.APPLE, 1).id("sample"))
                .collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("subject", ENTRY)
                        .objective("sample").discovered().requirementMode(CollectionRequirementMode.ANY)));
        assertThrows(IllegalArgumentException.class, () -> base(0).repeatable().phase(phase).build());
        var bounded = base(1200).repeatable().phase(phase).build();
        var spec = CollectionDefinitionSpecExporter.quest(bounded, null);
        assertFalse(new QuestSpecValidator().validate(spec).hasErrors());
        spec.collectionConfig.repeatCooldownTicks = 0;
        assertTrue(new QuestSpecValidator().validate(spec).getIssues().stream()
                .anyMatch(issue -> issue.path.equals("phases[0].collectionSheet.bindings[0]")));
    }

    @Test void aNewInvestigationCannotReuseALegacyBindingsConsumedQuantity() {
        var legacyId = ResourceLocation.parse("example:legacy_payment");
        var legacy = CollectionEntryBuilder.create(legacyId).category("field").legacyGameplay();
        var modern = CollectionEntryBuilder.create(ENTRY).category("field");
        var config = CollectionQuestConfigBuilder.create().category("field", "Field").entry(legacy).entry(modern).build();
        for (boolean legacyFirst : java.util.List.of(true, false)) {
            var one = EntryRequirementBuilder.create("old", legacyId).objective("sample");
            var two = EntryRequirementBuilder.create("new", ENTRY).objective("sample");
            var sheet = CollectionSheetBuilder.create().binding(legacyFirst ? one : two).binding(legacyFirst ? two : one);
            var phase = PhaseBuilder.create("survey").objective(ObjectiveBuilder.offer(Items.APPLE, 1).id("sample")).collectionSheet(sheet);
            assertThrows(IllegalArgumentException.class, () -> QuestBuilder.create("example:mixed_payment").mode(QuestMode.COLLECTION)
                    .collectionConfig(config).phase(phase).build());
        }
    }

    @Test void nonrepeatableEarlyBindingPaymentsRemainLegalWithoutMandatoryCostOrCooldown() {
        var holding = PhaseBuilder.create("survey").autoAdvanceOnComplete(false)
                .objective(ObjectiveBuilder.possess(Items.APPLE, 1).id("hold"))
                .collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("subject", ENTRY)
                        .objective("hold").reward("payment", new ItemReward(Items.EMERALD, 1))));
        assertDoesNotThrow(() -> base(0).phase(holding).build());
        assertDoesNotThrow(() -> base(0).cannotAbandon().phase(holding).build());
        assertThrows(IllegalArgumentException.class, () -> base(0).repeatable().cannotAbandon().phase(holding).build());
        var bounded = base(1200).phase(holding).build();
        assertFalse(bounded.isRepeatable()); assertTrue(bounded.isAbandonable());
        var data = new ArcQuestPlayer(UUID.randomUUID());
        data.recordCollectionAcceptance(bounded.getId().toString(), 100);
        data.addActiveQuest(new QuestRuntimeData(bounded.getId().toString(), "survey", 1, 0, 0, 0));
        data.markFailed(bounded.getId().toString());
        data.deserializeNBT(data.serializeNBT());
        assertTrue(data.isQuestFailed(bounded.getId().toString()));
        assertNull(data.getActiveQuest(bounded.getId().toString()));
        assertEquals(0, CollectionAcceptanceRules.cooldownRemaining(bounded, data, 200),
                "A nonrepeatable failed quest is already terminal; no additional acceptance cooldown is needed");
        assertEquals(0, CollectionAcceptanceRules.cooldownRemaining(bounded, data, 1300));
        assertDoesNotThrow(() -> base(0).phase(PhaseBuilder.create("survey")
                .objective(ObjectiveBuilder.offer(Items.APPLE, 1).id("submit"))
                .collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("subject", ENTRY)
                        .objective("submit").reward("payment", new ItemReward(Items.EMERALD, 1))))).build());
    }

    @Test void aOneTimeDiscoveryIntroductionCanPayOnlyItsFinalCompletionWithoutNeedingAReplayCost() {
        var introduction = base(0).reward(new ItemReward(Items.EMERALD, 1)).phase(discoveryPhase()).build();
        assertFalse(introduction.isRepeatable()); assertTrue(introduction.isAbandonable());
        var spec = QuestSpecJsonReader.read(QuestSpecJsonWriter.write(CollectionDefinitionSpecExporter.quest(introduction, null)));
        assertFalse(new QuestSpecValidator().validate(spec).hasErrors());
        assertDoesNotThrow(() -> new QuestSpecCompiler().compile(spec));
        assertThrows(IllegalArgumentException.class, () -> base(0).repeatable()
                .reward(new ItemReward(Items.EMERALD, 1)).phase(discoveryPhase()).build());
    }

    @Test void nonrepeatablePhaseAndMilestonePaymentsRemainLegalOnce() {
        assertDoesNotThrow(() -> base(0)
                .phase(discoveryPhase().reward(new ItemReward(Items.EMERALD, 1))).build());
        var node = new CollectionRewardNode("early_milestone", RewardScope.QUEST, EntryRewardGrantMode.MANUAL,
                java.util.List.of(new ItemReward(Items.EMERALD, 1)),
                java.util.List.of(new org.arcadia.arc_quest.quest.api.rule.collection.CompletedEntryCountRule(1)), null);
        var config = CollectionQuestConfigBuilder.create().category("field", "Field")
                .entry(CollectionEntryBuilder.create(ENTRY).category("field")).reward(node).build();
        assertDoesNotThrow(() -> QuestBuilder.create("example:early_milestone").mode(QuestMode.COLLECTION)
                .collectionConfig(config).phase(discoveryPhase()).build());
        var oneShot = QuestBuilder.create("example:early_milestone").mode(QuestMode.COLLECTION)
                .collectionConfig(config).phase(discoveryPhase()).build();
        var spec = CollectionDefinitionSpecExporter.quest(oneShot, null);
        assertFalse(new QuestSpecValidator().validate(spec).hasErrors());
        assertDoesNotThrow(() -> new QuestSpecCompiler().compile(spec));
        spec.repeatable = true;
        assertTrue(new QuestSpecValidator().validate(spec).getIssues().stream()
                .anyMatch(issue -> issue.path.equals("collectionConfig.rewardNodes[0]")));
    }

    @Test void nonrepeatableEarlyPaymentsAreLegalThroughTheJsonAuthoringPathButRepeatingNeedsFulfillment() {
        var bounded = base(0).phase(PhaseBuilder.create("survey").autoAdvanceOnComplete(false)
                .objective(ObjectiveBuilder.possess(Items.APPLE, 1).id("hold"))
                .collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("subject", ENTRY)
                        .objective("hold").reward("payment", new ItemReward(Items.EMERALD, 1))))).build();
        var spec = QuestSpecJsonReader.read(QuestSpecJsonWriter.write(CollectionDefinitionSpecExporter.quest(bounded, null)));
        assertFalse(spec.repeatable); assertTrue(spec.abandonable);
        assertFalse(new QuestSpecValidator().validate(spec).hasErrors());
        assertDoesNotThrow(() -> new QuestSpecCompiler().compile(spec));
        spec.repeatable = true;
        assertTrue(new QuestSpecValidator().validate(spec).getIssues().stream()
                .anyMatch(issue -> issue.path.equals("phases[0].collectionSheet.bindings[0]")));
        assertThrows(org.arcadia.arc_quest.quest.spec.compile.QuestCompileException.class, () -> new QuestSpecCompiler().compile(spec));
    }

    private static PhaseBuilder discoveryPhase() {
        return PhaseBuilder.create("survey").collectionSheet(CollectionSheetBuilder.create()
                .binding(EntryRequirementBuilder.create("subject", ENTRY).discovered()));
    }

    @Test void fieldMilestonesRequireRealInvestigationsWithoutRejectingTheTwoDiscoveryOnlyCandidates() {
        var quest = org.arcadia.arc_quest.quest.registry.CollectionFieldDemos.field(
                org.arcadia.arc_quest.quest.registry.CollectionFieldDemos.entries());
        assertDoesNotThrow(() -> CollectionGameplayValidation.validate(quest));
        var spec = QuestSpecJsonReader.read(QuestSpecJsonWriter.write(CollectionDefinitionSpecExporter.quest(quest, null)));
        assertFalse(new QuestSpecValidator().validate(spec).hasErrors());
        assertDoesNotThrow(() -> new QuestSpecCompiler().compile(spec));
    }

    @Test void aCraftMilestoneCannotUnlockImmediatelyFromHeldItemsOrAnExistingDiscovery() {
        var node = new CollectionRewardNode("new_craft", RewardScope.QUEST, EntryRewardGrantMode.MANUAL,
                java.util.List.of(new ItemReward(Items.EMERALD, 1)),
                java.util.List.of(new org.arcadia.arc_quest.quest.api.rule.collection.CompletedEntryCountRule(1)), null);
        var config = CollectionQuestConfigBuilder.create().category("field", "Field")
                .entry(CollectionEntryBuilder.create(ENTRY).category("field")).reward(node).build();
        var quest = QuestBuilder.create("example:new_craft_milestone").mode(QuestMode.COLLECTION).repeatable().collectionConfig(config)
                .phase(PhaseBuilder.create("survey").objective(ObjectiveBuilder.craft(Items.APPLE, 1).id("craft"))
                        .collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("subject", ENTRY).objective("craft")))).build();
        var records = new org.arcadia.arc_quest.quest.data.CollectionRecordState(); records.discover(ENTRY);
        var run = new QuestRuntimeData(quest.getId().toString(), "survey", 1, 0, 0, 0);
        assertFalse(org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector
                .project(quest, quest.getPhase("survey"), run, records).binding("subject").complete(),
                "Existing discovery and preparation cannot fabricate the new crafting action");
        var spec = QuestSpecJsonReader.read(QuestSpecJsonWriter.write(CollectionDefinitionSpecExporter.quest(quest, null)));
        assertFalse(new QuestSpecValidator().validate(spec).hasErrors());
    }

    private static QuestBuilder quest(ObjectiveBuilder objective, long cooldown) {
        return base(cooldown).repeatable().phase(PhaseBuilder.create("survey").objective(objective.id("action"))
                .collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("subject", ENTRY)
                        .objective("action").reward("payment", new ItemReward(Items.EMERALD, 1)))));
    }
    private static QuestBuilder base(long cooldown) {
        return QuestBuilder.create("example:repeat_fulfillment").mode(QuestMode.COLLECTION).collectionConfig(
                CollectionQuestConfigBuilder.create().category("field", "Field").repeatCooldownTicks(cooldown)
                        .entry(CollectionEntryBuilder.create(ENTRY).category("field")).build());
    }
}
