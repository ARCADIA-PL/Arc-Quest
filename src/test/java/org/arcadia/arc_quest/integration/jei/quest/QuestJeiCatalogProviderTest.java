package org.arcadia.arc_quest.integration.jei.quest;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.integration.jei.api.JeiCatalogEntry;
import org.arcadia.arc_quest.integration.jei.api.JeiDisplayAdapters;
import org.arcadia.arc_quest.integration.jei.api.JeiIngredient;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.PhaseBuilder;
import org.arcadia.arc_quest.quest.builder.QuestBuilder;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.data.CollectionRuntimeData;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QuestJeiCatalogProviderTest {
    private static final ResourceLocation TYPE = ResourceLocation.parse("test:jei_objective");
    private static final ObjectiveType OBJECTIVE = new ObjectiveType(TYPE, true, false, "", true, "item");
    private static final String QUEST = "test:jei_quest";

    @BeforeAll
    static void adapters() {
        // Empty alternatives keep this test focused on disclosure, not Minecraft's item registry bootstrap.
        JeiDisplayAdapters.registerObjective(TYPE, (objective, player) -> new JeiDisplayAdapters.Presentation(
                List.of(new JeiIngredient(List.of(), objective.getRequiredCount(), false, objective.getDisplayText())), List.of()));
        JeiDisplayAdapters.registerReward(TestReward.class, (reward, player) -> new JeiDisplayAdapters.Presentation(
                List.of(new JeiIngredient(List.of(), 1, false, Component.literal(reward.name))), List.of()));
    }

    @Test
    void unacceptedQuestNeverLeaksEvenWhenDefinitionAndRewardsExist() {
        var rows = new ArrayList<JeiCatalogEntry>();
        QuestJeiCatalogProvider.collectQuest(null, new ArcQuestPlayer(UUID.randomUUID()), definition(), null, rows::add);
        assertTrue(rows.isEmpty());
    }

    @Test
    void activePhaseRequirementsAndRewardsExcludeHiddenObjectivesAndAlternateBranch() {
        var data = new ArcQuestPlayer(UUID.randomUUID());
        var runtime = new QuestRuntimeData(QUEST, "left", 2, 0, 0, 0);
        data.addActiveQuest(runtime);
        var rows = new ArrayList<JeiCatalogEntry>();
        QuestJeiCatalogProvider.collectQuest(null, data, definition(), runtime, rows::add);
        assertEquals(3, rows.size()); // completion reward, visible requirement, reached-phase reward
        assertEquals(1, rows.stream().filter(row -> row.kind() == JeiCatalogEntry.Kind.QUEST_REQUIREMENT).count());
        assertTrue(rows.stream().noneMatch(row -> row.id().contains("right") || row.id().contains("hidden")));
        var requirement = rows.stream().filter(row -> row.kind() == JeiCatalogEntry.Kind.QUEST_REQUIREMENT).findFirst().orElseThrow();
        assertEquals(7, requirement.inputs().get(0).amount());
        assertTrue(requirement.outputs().isEmpty());
        assertEquals("left", requirement.navigationDetail());
    }

    @Test
    void completedQuestUsesRecordedBranchWithoutGuessingHistoryForOldSaves() {
        var data = new ArcQuestPlayer(UUID.randomUUID());
        data.markCompleted(QUEST);
        var rows = new ArrayList<JeiCatalogEntry>();
        QuestJeiCatalogProvider.collectQuest(null, data, definition(), null, rows::add);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).id().endsWith("/complete"));
        rows.clear();
        var known = new JeiQuestKnowledge.KnownQuest(Set.of("left"), Set.of(), Set.of(), false);
        QuestJeiCatalogProvider.collectQuest(null, data, definition(), null, known, rows::add);
        assertEquals(3, rows.size());
        assertTrue(rows.stream().noneMatch(row -> row.id().contains("right")));
    }

    @Test
    void collectionMilestoneRequiresAuthoritativeUnlockAndCurrentCategoryVisibility() {
        var data = new ArcQuestPlayer(UUID.randomUUID());
        var runtime = new QuestRuntimeData(QUEST, "entry", 1, 0, 0, 0);
        var collection = new CollectionRuntimeData();
        collection.markVisible("entry");
        collection.markDiscovered("entry");
        runtime.setCollectionData(collection);
        data.addActiveQuest(runtime);
        var definition = collectionDefinition();
        var rows = new ArrayList<JeiCatalogEntry>();
        QuestJeiCatalogProvider.collectQuest(null, data, definition, runtime, rows::add);
        assertTrue(rows.isEmpty(), "Hidden category suppresses underlying requirements and rewards");
        data.setFlag("show_category");
        QuestJeiCatalogProvider.collectQuest(null, data, definition, runtime, rows::add);
        assertEquals(1, rows.size(), "Only the visible requirement, never generic collection rewards");
        assertTrue(rows.stream().noneMatch(row -> row.kind() == JeiCatalogEntry.Kind.QUEST_REWARD));
        rows.clear();
        collection.markRewardUnlocked("milestone");
        QuestJeiCatalogProvider.collectQuest(null, data, definition, runtime, rows::add);
        assertEquals(2, rows.size());
        assertTrue(rows.stream().anyMatch(row -> row.id().endsWith("/milestone/milestone")));

        data.markCompleted(QUEST);
        var known = new JeiQuestKnowledge.KnownQuest(Set.of("entry"), Set.of("milestone"), Set.of("milestone"), true);
        rows.clear();
        QuestJeiCatalogProvider.collectQuest(null, data, definition, null, known, rows::add);
        assertEquals(2, rows.size(), "Terminal history retains only authorized collection sources");
        assertTrue(rows.stream().flatMap(row -> row.notes().stream()).anyMatch(note -> note.getString().equals("Already claimed")));
    }

    private static QuestDefinition collectionDefinition() {
        var milestone = new CollectionRewardNode("milestone", RewardScope.CATEGORY, EntryRewardGrantMode.MANUAL,
                List.of(new TestReward("Milestone")), List.of(), "category");
        var category = new CollectionCategoryDefinition("category", QuestText.literal("Category"), null, 0,
                List.of(), List.of(milestone), List.of(ICondition.flagSet("show_category")));
        var config = new CollectionQuestConfig(List.of(category), List.of(), List.of(), null, null, false, true, true);
        var entry = new CollectionEntryConfig("category", VisibilityMode.VISIBLE_BY_DEFAULT, HiddenPresentationMode.FULLY_HIDDEN,
                List.of(), CountingMode.BINARY, 1, false, false, 0, EntryRewardGrantMode.AUTO, List.of(), 0, false);
        return QuestBuilder.create(QUEST).mode(QuestMode.COLLECTION).collectionConfig(config)
                .phase(PhaseBuilder.create("entry").collectionEntryConfig(entry).objective(objective("entry_objective", false))
                        .reward(new TestReward("Not granted by collection engine")))
                .reward(new TestReward("Not granted by collection engine")).build();
    }

    private static QuestDefinition definition() {
        return QuestBuilder.create(QUEST).displayName("Quest")
                .phase(PhaseBuilder.create("left").displayName("Left")
                        .objective(objective("visible", false)).objective(objective("hidden", true))
                        .reward(new TestReward("Left reward")))
                .phase(PhaseBuilder.create("right").displayName("Spoiler").objective(objective("secret", false))
                        .reward(new TestReward("Secret reward")))
                .reward(new TestReward("Completion")).build();
    }

    private static ObjectiveEntry objective(String id, boolean hidden) {
        return new ObjectiveEntry(id, OBJECTIVE, ResourceLocation.parse("minecraft:diamond"), 7,
                QuestText.literal("Requirement"), hidden, false, Map.of(), List.of(), null);
    }

    private record TestReward(String name) implements IReward {
        @Override public void grant(ServerPlayer player) { fail("Catalog must not grant rewards"); }
        @Override public String describe() { return name; }
    }
}
