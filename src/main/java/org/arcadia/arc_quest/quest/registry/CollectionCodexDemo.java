package org.arcadia.arc_quest.quest.registry;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.api.ArcQuestAPI;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.api.rule.collection.AllEntriesCompleteRule;
import org.arcadia.arc_quest.quest.api.rule.collection.CategoryCompletedCountRule;
import org.arcadia.arc_quest.quest.api.rule.collection.CompletedEntryCountRule;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.quest.builder.PhaseBuilder;
import org.arcadia.arc_quest.quest.builder.QuestBuilder;
import org.arcadia.arc_quest.quest.reward.ItemReward;

import java.util.List;

public final class CollectionCodexDemo {

    private static final String QUEST_ID = "arc_quest:collection_codex_demo";
    private static final String CAT_HOSTILE = "hostile_mobs";
    private static final String CAT_RESOURCES = "field_resources";

    private CollectionCodexDemo() {
    }

    public static void registerAll() {
        ArcQuestAPI.registerQuest(
                QuestBuilder.create(QUEST_ID)
                        .category(QuestCategory.ADVENTURE)
                        .displayName(Component.literal("Collection Codex Demo"))
                        .description(Component.literal("Objective Framework 2.0 collection quest demo."))
                        .sortOrder(9000)
                        .themeColor(ChatFormatting.AQUA)
                        .mode(QuestMode.COLLECTION)
                        .collectionConfig(createConfig())
                        .phase(PhaseBuilder.create("arc_quest:codex_zombie")
                                .displayName(Component.literal("Zombie Record"))
                                .description(Component.literal("Defeat one zombie to record it in the codex."))
                                .objective(ObjectiveBuilder.kill(EntityType.ZOMBIE, 1))
                                .collectionEntryConfig(entry(CAT_HOSTILE, VisibilityMode.VISIBLE_BY_DEFAULT, HiddenPresentationMode.FULLY_HIDDEN, CountingMode.BINARY, 1, 1, false, false, 0)))
                        .phase(PhaseBuilder.create("arc_quest:codex_skeleton")
                                .displayName(Component.literal("Skeleton Record"))
                                .description(Component.literal("Defeat skeletons to complete this entry."))
                                .objective(ObjectiveBuilder.kill(EntityType.SKELETON, 3))
                                .collectionEntryConfig(entry(CAT_HOSTILE, VisibilityMode.VISIBLE_BY_DEFAULT, HiddenPresentationMode.FULLY_HIDDEN, CountingMode.ACCUMULATE, 3, 3, false, false, 10)))
                        .phase(PhaseBuilder.create("arc_quest:codex_spider_hidden")
                                .displayName(Component.literal("Spider Record"))
                                .description(Component.literal("A hidden entry used to verify placeholder presentation."))
                                .objective(ObjectiveBuilder.kill(EntityType.SPIDER, 1))
                                .collectionEntryConfig(entry(CAT_HOSTILE, VisibilityMode.HIDDEN_BY_DEFAULT, HiddenPresentationMode.PLACEHOLDER, CountingMode.BINARY, 1, 5, false, false, 0)))
                        .phase(PhaseBuilder.create("arc_quest:codex_bone")
                                .displayName(Component.literal("Bone Sample"))
                                .description(Component.literal("Collect bones to test accumulated collection progress."))
                                .objective(ObjectiveBuilder.collect(Items.BONE, 5))
                                .collectionEntryConfig(entry(CAT_RESOURCES, VisibilityMode.VISIBLE_BY_DEFAULT, HiddenPresentationMode.FULLY_HIDDEN, CountingMode.ACCUMULATE, 5, 1, false, false, 16)))
                        .phase(PhaseBuilder.create("arc_quest:codex_rotten_flesh_unique")
                                .displayName(Component.literal("Rotten Flesh Sample"))
                                .description(Component.literal("A unique-set entry for collection dispatcher coverage."))
                                .objective(ObjectiveBuilder.collect(Items.ROTTEN_FLESH, 1))
                                .collectionEntryConfig(entry(CAT_RESOURCES, VisibilityMode.VISIBLE_BY_DEFAULT, HiddenPresentationMode.FULLY_HIDDEN, CountingMode.UNIQUE_SET, 1, 2, false, false, 1)))
                        .build()
        );
    }

    private static CollectionQuestConfig createConfig() {
        CollectionCategoryDefinition hostile = new CollectionCategoryDefinition(
                CAT_HOSTILE,
                QuestText.literal("Hostile Mobs"),
                null,
                0,
                List.of(new CompletedEntryCountRule(2)),
                List.of(new CollectionRewardNode(
                        "arc_quest:codex_hostile_reward",
                        RewardScope.CATEGORY,
                        EntryRewardGrantMode.AUTO,
                        List.of(new ItemReward(Items.ARROW, 16)),
                        List.of(new CompletedEntryCountRule(2)),
                        CAT_HOSTILE)),
                List.of()
        );
        CollectionCategoryDefinition resources = new CollectionCategoryDefinition(
                CAT_RESOURCES,
                QuestText.literal("Field Resources"),
                null,
                1,
                List.of(new CompletedEntryCountRule(2)),
                List.of(new CollectionRewardNode(
                        "arc_quest:codex_resource_reward",
                        RewardScope.CATEGORY,
                        EntryRewardGrantMode.MANUAL,
                        List.of(new ItemReward(Items.EMERALD, 1)),
                        List.of(new CompletedEntryCountRule(2)),
                        CAT_RESOURCES)),
                List.of()
        );
        CollectionCompletionRule allEntries = new AllEntriesCompleteRule();
        return new CollectionQuestConfig(
                List.of(hostile, resources),
                List.of(new CategoryCompletedCountRule(2)),
                List.of(new CollectionRewardNode(
                        "arc_quest:codex_quest_reward",
                        RewardScope.QUEST,
                        EntryRewardGrantMode.MANUAL,
                        List.of(new ItemReward(Items.DIAMOND, 1)),
                        List.of(allEntries),
                        QUEST_ID)),
                TrackerPresentationMode.SUMMARY_WITH_FEED,
                CollectionPresentationMode.LIST,
                false,
                true,
                true
        );
    }

    private static CollectionEntryConfig entry(String categoryId,
                                               VisibilityMode visibilityMode,
                                               HiddenPresentationMode hiddenMode,
                                               CountingMode countingMode,
                                               int completionTarget,
                                               int sortOrder,
                                               boolean repeatableProgress,
                                               boolean repeatableCompletion,
                                               int maxCount) {
        return new CollectionEntryConfig(
                categoryId,
                visibilityMode,
                hiddenMode,
                List.of(),
                countingMode,
                completionTarget,
                repeatableProgress,
                repeatableCompletion,
                maxCount,
                EntryRewardGrantMode.AUTO,
                List.of(),
                sortOrder,
                true
        );
    }
}
