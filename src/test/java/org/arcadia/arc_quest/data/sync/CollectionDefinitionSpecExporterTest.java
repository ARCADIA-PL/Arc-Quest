package org.arcadia.arc_quest.data.sync;

import com.google.gson.Gson;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.api.rule.collection.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.CollectionRecordState;
import org.arcadia.arc_quest.quest.reward.ItemReward;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.arcadia.arc_quest.quest.spec.compile.QuestSpecCompiler;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonReader;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CollectionDefinitionSpecExporterTest {
    private static final ResourceLocation ENTRY = ResourceLocation.parse("example:milestone_entry");
    private static final String QUEST_ID = "example:milestone_quest";
    private static final Gson GSON = new Gson();
    @BeforeAll static void setup() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void javaQuestAndCategoryMilestonesSurviveAuthorizedWireAndClientCompilation() throws Exception {
        var questNode = new CollectionRewardNode("quest_milestone", RewardScope.QUEST, EntryRewardGrantMode.AUTO,
                List.of(new ItemReward(Items.EMERALD, 2)),
                List.of(new AllEntriesCompleteRule(), new CompletedEntryCountRule(2), new CategoryCompletedCountRule(1),
                        new CompletedEntryRatioRule(0.375f), new CategoryCompletedRatioRule(1f / 3f),
                        new AndCollectionRule(new CompletedEntryCountRule(1),
                                new OrCollectionRule(new NotCollectionRule(new CompletedEntryCountRule(8)),
                                        new CategoryCompletedCountRule(1)), new AllEntriesCompleteRule())), QUEST_ID);
        var categoryNode = new CollectionRewardNode("category_milestone", RewardScope.CATEGORY, EntryRewardGrantMode.MANUAL,
                List.of(new ItemReward(Items.DIAMOND, 3)),
                List.of(new OrCollectionRule(new CategoryCompletedRatioRule(0.375f),
                        new CompletedEntryCountRule(3), new NotCollectionRule(new AllEntriesCompleteRule()))), "field");
        var category = new CollectionCategoryDefinition("field", QuestText.literal("Field"), null, 0,
                List.of(new CompletedEntryCountRule(1)), List.of(categoryNode), List.of());
        QuestDefinition quest = quest(category, questNode);
        QuestSpec decoded = actualWire(quest);

        assertEquals(1, decoded.collectionConfig.rewardNodes.size());
        assertEquals(1, decoded.collectionConfig.categories.get(0).rewardNodes.size());
        var q = decoded.collectionConfig.rewardNodes.get(0);
        assertEquals("quest_milestone", q.nodeId);
        assertEquals("QUEST", q.scope); assertEquals("AUTO", q.grantMode); assertEquals(QUEST_ID, q.scopeRefId);
        assertEquals("minecraft:emerald", q.rewards.get(0).itemId); assertEquals(2, q.rewards.get(0).count);
        var c = decoded.collectionConfig.categories.get(0).rewardNodes.get(0);
        assertEquals("category_milestone", c.nodeId);
        assertEquals("CATEGORY", c.scope); assertEquals("MANUAL", c.grantMode); assertEquals("field", c.scopeRefId);
        assertEquals("minecraft:diamond", c.rewards.get(0).itemId); assertEquals(3, c.rewards.get(0).count);

        var compiled = QuestSpecCompiler.compileClientPresentation(decoded, Map.of());
        var compiledQuestNode = compiled.getCollectionConfig().getQuestRewardNodes().get(0);
        assertEquals(6, compiledQuestNode.getUnlockRules().size());
        assertEquals(0.375f, ((CompletedEntryRatioRule) compiledQuestNode.getUnlockRules().get(3)).getRequiredRatio());
        assertEquals(1f / 3f, ((CategoryCompletedRatioRule) compiledQuestNode.getUnlockRules().get(4)).getRequiredRatio());
        var exportedAgain = CollectionDefinitionSpecExporter.quest(compiled, null);
        assertEquals(GSON.toJsonTree(decoded.collectionConfig.rewardNodes),
                GSON.toJsonTree(exportedAgain.collectionConfig.rewardNodes));
        assertEquals(GSON.toJsonTree(decoded.collectionConfig.categories.get(0)),
                GSON.toJsonTree(exportedAgain.collectionConfig.categories.get(0)));
        var item = assertInstanceOf(ItemReward.class, compiledQuestNode.getRewards().get(0));
        assertSame(Items.EMERALD, item.getItem()); assertEquals(2, item.getCount());
    }

    @Test void customRulesAreNotExecutedAndCannotRemoveTheRewardNodeOrPartiallyChangeCompoundRules() throws Exception {
        CollectionCompletionRule custom = context -> { throw new AssertionError("A server-only custom rule was executed"); };
        var node = new CollectionRewardNode("custom_milestone", RewardScope.QUEST, EntryRewardGrantMode.MANUAL,
                List.of(new ItemReward(Items.GOLD_INGOT, 1)),
                List.of(custom, new NotCollectionRule(custom),
                        new OrCollectionRule(new CompletedEntryCountRule(1), custom), new CompletedEntryCountRule(2)), QUEST_ID);
        var category = new CollectionCategoryDefinition("field", QuestText.literal("Field"), null, 0,
                List.of(), List.of(), List.of());
        var decoded = actualWire(quest(category, node));
        assertEquals(1, decoded.collectionConfig.rewardNodes.size());
        var output = decoded.collectionConfig.rewardNodes.get(0);
        assertEquals("custom_milestone", output.nodeId);
        assertEquals(1, output.completionRules.size());
        assertEquals("completed_entry_count", output.completionRules.get(0).type);
        assertEquals(2, output.completionRules.get(0).value);
        var compiled = QuestSpecCompiler.compileClientPresentation(decoded, Map.of());
        assertEquals(EntryRewardGrantMode.MANUAL, compiled.getCollectionConfig().getQuestRewardNodes().get(0).getGrantMode());
        assertEquals(Items.GOLD_INGOT, ((ItemReward) compiled.getCollectionConfig().getQuestRewardNodes().get(0).getRewards().get(0)).getItem());
    }

    @Test void emptyLogicalRulesAndLegacyPercentageThresholdsRemainRepresentable() throws Exception {
        var node = new CollectionRewardNode("empty_logic", RewardScope.QUEST, EntryRewardGrantMode.AUTO,
                List.of(), List.of(new AndCollectionRule(), new OrCollectionRule(), new NotCollectionRule(null)), QUEST_ID);
        var category = new CollectionCategoryDefinition("field", QuestText.literal("Field"), null, 0,
                List.of(), List.of(), List.of());
        var decoded = actualWire(quest(category, node));
        var rules = decoded.collectionConfig.rewardNodes.get(0).completionRules;
        assertEquals("completed_entry_ratio", rules.get(0).type); assertEquals(0f, rules.get(0).ratio.floatValue());
        assertEquals("not", rules.get(1).type); assertEquals(0f, rules.get(1).left.ratio.floatValue());
        assertEquals("completed_entry_ratio", rules.get(2).type);
        rules.get(0).ratio = null; rules.get(0).value = 45;
        var compiled = QuestSpecCompiler.compileClientPresentation(decoded, Map.of());
        assertEquals(0.45f, ((CompletedEntryRatioRule) compiled.getCollectionConfig().getQuestRewardNodes().get(0)
                .getUnlockRules().get(0)).getRequiredRatio());
    }

    private static QuestDefinition quest(CollectionCategoryDefinition category, CollectionRewardNode node) {
        return QuestBuilder.create(QUEST_ID).category(QuestCategory.ADVENTURE).mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category(category).reward(node)
                        .entry(CollectionEntryBuilder.create(ENTRY).category("field")).build())
                .phase(PhaseBuilder.create("survey").autoAdvanceOnComplete(false)
                        .collectionSheet(CollectionSheetBuilder.create()
                                .binding(EntryRequirementBuilder.create("entry", ENTRY).discovered()))).build();
    }

    private static QuestSpec actualWire(QuestDefinition quest) throws java.io.IOException {
        var projected = CollectionContentDisclosure.project(DatapackContentSnapshot.empty(5), new CollectionRecordState(),
                ignored -> true, ignored -> quest, null, List.of(quest));
        String wire = DatapackContentCodec.decode(DatapackContentCodec.encode(projected))
                .documents(DatapackContentModule.QUEST).get(0);
        return QuestSpecJsonReader.read(wire);
    }
}
