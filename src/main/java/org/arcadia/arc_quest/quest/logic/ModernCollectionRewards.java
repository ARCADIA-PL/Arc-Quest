package org.arcadia.arc_quest.quest.logic;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionRewardResolver;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;

/** Modern milestone nodes reuse the original reward IDs, grant modes and saved claim receipts. */
public final class ModernCollectionRewards {
    private ModernCollectionRewards() {}

    /** Called after binding results have been latched, and before a task is archived. */
    public static boolean updateRewards(ServerPlayer player, ArcQuestPlayer data, QuestDefinition quest, QuestRuntimeData runtime) {
        if (!quest.hasCollectionSheets() || quest.getCollectionConfig() == null || runtime.getCollectionData() == null) return false;
        var collection = runtime.getCollectionData();
        int beforeUnlocked = collection.getUnlockedRewardIds().size();
        int beforeClaimed = collection.getClaimedRewardIds().size();
        var context = new CollectionRuleContext(player, quest, runtime, collection, data, null);
        for (CollectionRewardNode node : quest.getCollectionConfig().getQuestRewardNodes()) {
            inheritLegacyReceipt(data, collection, node);
            CollectionRewardResolver.tryGrantRewardNode(player, collection, context, node, quest.getId().toString());
        }
        for (CollectionCategoryDefinition category : quest.getCollectionConfig().getCategories()) {
            var categoryContext = new CollectionRuleContext(player, quest, runtime, collection, data, category.getCategoryId());
            for (CollectionRewardNode node : category.getRewardNodes()) {
                inheritLegacyReceipt(data, collection, node);
                CollectionRewardResolver.tryGrantRewardNode(player, collection, categoryContext, node, category.getCategoryId());
            }
        }
        return beforeUnlocked != collection.getUnlockedRewardIds().size() || beforeClaimed != collection.getClaimedRewardIds().size();
    }

    private static void inheritLegacyReceipt(ArcQuestPlayer data, org.arcadia.arc_quest.quest.data.CollectionRuntimeData collection, CollectionRewardNode node) {
        if (data.getCollectionRecords().isLegacyRewardClaimed(node.getRewardNodeId())) {
            collection.markRewardUnlocked(node.getRewardNodeId());
            collection.markRewardClaimed(node.getRewardNodeId());
        }
    }
}
