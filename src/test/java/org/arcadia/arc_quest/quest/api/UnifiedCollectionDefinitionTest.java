package org.arcadia.arc_quest.quest.api;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.builder.CollectionEntryBuilder;
import org.arcadia.arc_quest.quest.builder.CollectionQuestConfigBuilder;
import org.arcadia.arc_quest.quest.builder.EntryRequirementBuilder;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.quest.registry.CollectionEntryRegistry;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class UnifiedCollectionDefinitionTest {
    private static final ResourceLocation ENTRY = ResourceLocation.parse("example:outcome_api");
    @BeforeAll static void initialize() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void categoryOrderSupportsExplicitNegativeValuesWithoutChangingDeclarationDefaults() {
        var categories = CollectionQuestConfigBuilder.create()
                .category("first", "First")
                .category("priority", "Priority", -10)
                .category("third", "Third")
                .category("priority_tie", "Priority tie", -10)
                .build().getCategories();

        assertEquals(List.of("first", "priority", "third", "priority_tie"),
                categories.stream().map(CollectionCategoryDefinition::getCategoryId).toList());
        assertEquals(List.of(0, -10, 2, -10),
                categories.stream().map(CollectionCategoryDefinition::getSortOrder).toList());
    }

    @Test void simpleEntriesDefaultUnifiedAndOutcomesContainNoObjectiveGroup() {
        var entry = CollectionEntryBuilder.create(ENTRY).category("field").outcome("anatomy", "Anatomy").outcomeReward("anatomy", "first").build();
        assertTrue(entry.isUnifiedGameplay()); assertTrue(entry.getResearchObjectives().isEmpty());
        assertEquals("Anatomy", entry.getOutcome("anatomy").getDisplayName().getString());
        var binding = EntryRequirementBuilder.create("investigate", ENTRY).objective("kill").recordOutcome("anatomy").reward("payment").build();
        assertEquals(List.of(CollectionRecordRequirement.discovered()), binding.getRecordRequirements());
        assertEquals(CollectionEntryRewardTrigger.BINDING_COMPLETE, binding.getRewards().get(0).trigger());
        assertEquals(CollectionRewardPreviewVisibility.PUBLIC, binding.getRewards().get(0).previewVisibility());
    }

    @Test void legacyConstructorsKeepDisclosureAndLegacyBuilderCannotMixNewFeatures() {
        var reward = new CollectionEntryRewardDefinition("old", CollectionEntryRewardTrigger.RESEARCH_COMPLETE, List.of());
        assertEquals(CollectionRewardPreviewVisibility.UNLOCKED_ONLY, reward.previewVisibility());
        var old = CollectionEntryBuilder.create(ENTRY).category("field").research(ObjectiveBuilder.custom(ENTRY, 5).id("study")).build();
        assertFalse(old.isUnifiedGameplay());
        assertThrows(IllegalStateException.class, () -> CollectionEntryBuilder.create(ENTRY).category("field").outcome("anatomy", "Anatomy").research(ObjectiveBuilder.custom(ENTRY, 5).id("study")));
        assertThrows(IllegalStateException.class, () -> CollectionEntryBuilder.create(ENTRY).category("field").research(ObjectiveBuilder.custom(ENTRY, 5).id("study")).outcome("anatomy", "Anatomy"));
    }

    @Test void sourceCannotBeDiscoveryOnlyOrSelfReferentialAndMigrationRequiresOldThresholds() {
        assertThrows(IllegalArgumentException.class, () -> EntryRequirementBuilder.create("free", ENTRY).discovered().recordOutcome("anatomy").build());
        assertThrows(IllegalArgumentException.class, () -> EntryRequirementBuilder.create("loop", ENTRY).objective("act").recordOutcome("anatomy").requiresOutcome("anatomy").build());
        assertThrows(IllegalArgumentException.class, () -> CollectionEntryBuilder.create(ENTRY).category("field").outcome("anatomy", "Anatomy").migrateResearchStep("study", "anatomy").build());
        var entry = CollectionEntryBuilder.create(ENTRY).category("field").outcome("anatomy", "Anatomy")
                .migrateResearchStep(ObjectiveBuilder.custom(ENTRY, 5).id("study").build(), "anatomy").build();
        assertEquals(5, entry.getLegacyResearchObjectives().get(0).getRequiredCount());
        assertTrue(entry.getResearchObjectives().isEmpty());
    }

    @Test void sharedEntryIdentityIncludesOutcomesAndRewardDisclosure() {
        var first = CollectionEntryBuilder.create(ENTRY).category("field").outcome("anatomy", "Anatomy").outcomeReward("anatomy", "first").build();
        var equal = CollectionEntryBuilder.create(ENTRY).category("field").outcome("anatomy", "Anatomy").outcomeReward("anatomy", "first").build();
        var other = CollectionEntryBuilder.create(ENTRY).category("field").outcome("anatomy", "Other").outcomeReward("anatomy", "first").build();
        assertTrue(CollectionEntryRegistry.equivalent(first, equal)); assertFalse(CollectionEntryRegistry.equivalent(first, other));
        var secret = CollectionEntryBuilder.create(ENTRY).category("field").outcome("anatomy", "Anatomy")
                .outcomeReward("anatomy", "first", EntryRewardGrantMode.MANUAL, CollectionRewardPreviewVisibility.UNLOCKED_ONLY).build();
        assertFalse(CollectionEntryRegistry.equivalent(first, secret));
    }
}
