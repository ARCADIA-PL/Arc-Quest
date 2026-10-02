package org.arcadia.arc_quest.quest.logic;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.CollectionRecordState;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CollectionAcceptanceFeasibilityTest {
    private static final ResourceLocation A = ResourceLocation.parse("example:known");
    private static final ResourceLocation B = ResourceLocation.parse("example:fresh");
    @BeforeAll static void setup() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void remainingNewDiscoveriesMustReachQuotaAndCheckingDoesNotMutateKnowledge() {
        var phase = PhaseBuilder.create("survey").collectionSheet(CollectionSheetBuilder.create().quota(2)
                .binding(fresh("a", A)).binding(fresh("b", B))).build();
        var quest = quest(phase).build(); var records = records();
        var result = CollectionAcceptanceFeasibility.project(quest, phase, records);
        assertEquals(1, result.possibleCount()); assertEquals(2, result.requiredCount());
        assertFalse(result.feasible()); assertFalse(CollectionAcceptanceFeasibility.canAccept(quest, phase, records));
        assertTrue(records.isDiscovered(A)); assertFalse(records.isDiscovered(B));
    }

    @Test void anyActionCanRescueAnOldDiscoveryButOptionalActionCannot() {
        var phase = PhaseBuilder.create("survey").objective(ObjectiveBuilder.custom(A, 2).id("action"))
                .objective(ObjectiveBuilder.custom(A, 1).id("extra").optional())
                .collectionSheet(CollectionSheetBuilder.create().quota(1)
                        .binding(fresh("a", A).objective("action").requirementMode(CollectionRequirementMode.ANY))
                        .binding(fresh("b", A).objective("extra").requirementMode(CollectionRequirementMode.ANY)))
                .build();
        var quest = quest(phase).build();
        assertEquals(1, CollectionAcceptanceFeasibility.project(quest, phase, records()).possibleCount());
        assertTrue(CollectionAcceptanceFeasibility.canAccept(quest, phase, records()));
    }

    @Test void allRequirementsAndDistinctEntryBindingsUseAndAndOptionalBindingsNeverCount() {
        var phase = PhaseBuilder.create("survey").objective(ObjectiveBuilder.custom(A, 1).id("action"))
                .collectionSheet(CollectionSheetBuilder.create().quota(1).countDistinctEntries(true)
                        .binding(fresh("a", A))
                        .binding(EntryRequirementBuilder.create("other_a", A).objective("action"))
                        .binding(fresh("b", B).optional())).build();
        var quest = quest(phase).build();
        assertEquals(0, CollectionAcceptanceFeasibility.project(quest, phase, records()).possibleCount());
        assertFalse(CollectionAcceptanceFeasibility.canAccept(quest, phase, records()));
        var all = PhaseBuilder.create("survey").objective(ObjectiveBuilder.custom(A, 1).id("action"))
                .collectionSheet(CollectionSheetBuilder.create().binding(fresh("a", A).objective("action"))).build();
        assertFalse(CollectionAcceptanceFeasibility.project(quest(all).build(), all, records()).feasible());
    }

    @Test void optionalImpossibleBranchIsIgnoredUnlessCompletionPolicyRequiresIt() {
        var initial = PhaseBuilder.create("survey").collectionSheet(CollectionSheetBuilder.create()
                .binding(EntryRequirementBuilder.create("a", A).discovered())).build();
        var impossible = PhaseBuilder.create("old_branch").collectionSheet(CollectionSheetBuilder.create()
                .binding(fresh("old", A))).enterWhen(ICondition.flagSet("later_choice"), false).build();
        for (QuestCompletionPolicy policy : new QuestCompletionPolicy[]{QuestCompletionPolicy.ANY, QuestCompletionPolicy.SPECIFIC_PHASE}) {
            var builder = quest(initial).phase(impossible).completionPolicy(policy);
            if (policy == QuestCompletionPolicy.SPECIFIC_PHASE) builder.completionTargetPhase("survey");
            assertTrue(CollectionAcceptanceFeasibility.canAccept(builder.build(), initial, records()));
        }
        assertFalse(CollectionAcceptanceFeasibility.canAccept(quest(initial).phase(impossible).build(), initial, records()));
        assertFalse(CollectionAcceptanceFeasibility.canAccept(quest(initial).phase(impossible)
                .completionPolicy(QuestCompletionPolicy.N_OF_M).completionRequiredCount(2).build(), initial, records()));
    }

    private static EntryRequirementBuilder fresh(String id, ResourceLocation entry) {
        return EntryRequirementBuilder.create(id, entry).discovered().recordPolicy(CollectionRecordPolicy.NEW_DISCOVERIES);
    }
    private static CollectionRecordState records() { var records = new CollectionRecordState(); records.discover(A); return records; }
    private static QuestBuilder quest(PhaseDefinition phase) {
        return QuestBuilder.create("example:acceptance").mode(QuestMode.COLLECTION).initialPhase("survey")
                .collectionConfig(CollectionQuestConfigBuilder.create().category("field", "Field")
                        .entry(CollectionEntryBuilder.create(A).category("field"))
                        .entry(CollectionEntryBuilder.create(B).category("field")).build()).phase(phase);
    }
}
