package org.arcadia.arc_quest.quest.logic.profile.collection;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.data.CollectionRuntimeData;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import javax.annotation.Nullable;

public final class CollectionRewardResolver {

    private CollectionRewardResolver() {
    }

    @Nullable
    public static CollectionRewardNode findRewardNode(QuestDefinition def, CollectionQuestConfig config, String rewardNodeId) {
        if (def == null || config == null || rewardNodeId == null || rewardNodeId.isEmpty()) return null;
        for (CollectionRewardNode node : config.getQuestRewardNodes()) {
            if (rewardNodeId.equals(node.getRewardNodeId())) return node;
        }
        for (CollectionCategoryDefinition category : config.getCategories()) {
            for (CollectionRewardNode node : category.getRewardNodes()) {
                if (rewardNodeId.equals(node.getRewardNodeId())) return node;
            }
        }
        for (String phaseId : def.getPhaseIds()) {
            PhaseDefinition phase = def.getPhase(phaseId);
            if (phase == null || phase.getCollectionEntryConfig() == null) continue;
            for (CollectionRewardNode node : phase.getCollectionEntryConfig().getRewardNodes()) {
                if (rewardNodeId.equals(node.getRewardNodeId())) return node;
            }
        }
        return null;
    }

    public static void tryGrantRewardNode(ServerPlayer player,
                                          CollectionRuntimeData collectionData,
                                          CollectionRuleContext context,
                                          CollectionRewardNode node,
                                          String ownerId) {
        if (player == null || collectionData == null || context == null || node == null || node.getRewardNodeId() == null)
            return;
        if (node.getOwnerId() != null && ownerId != null && !node.getOwnerId().equals(ownerId)) return;
        if (collectionData.isRewardClaimed(node.getRewardNodeId())) return;
        if (collectionData.isRewardUnlocked(node.getRewardNodeId()) && node.getGrantMode() == EntryRewardGrantMode.MANUAL)
            return;
        boolean unlocked = node.getUnlockRules().isEmpty() || CollectionRuleEvaluator.all(context, node.getUnlockRules());
        if (!unlocked) return;
        collectionData.markRewardUnlocked(node.getRewardNodeId());
        if (node.getGrantMode() == EntryRewardGrantMode.AUTO) {
            grantNodeRewards(player, collectionData, node);
        }
    }

    public static void grantNodeRewards(ServerPlayer player, CollectionRuntimeData collectionData, CollectionRewardNode node) {
        if (player == null || collectionData == null || node == null) return;
        if (collectionData.isRewardClaimed(node.getRewardNodeId())) return;
        // Record an at-most-once receipt before invoking arbitrary reward callbacks. A callback can
        // refresh the quest, manually claim this node again, or trigger another native server event.
        // Keep the receipt if a reward throws: external side effects cannot safely be rolled back.
        collectionData.markRewardClaimed(node.getRewardNodeId());
        for (IReward reward : node.getRewards()) {
            try {
                if (reward != null) reward.grant(player);
            } catch (Exception e) {
                // Continue the remaining items and lifecycle publication just like normal quest rewards.
                ArcQuestLog.error(ArcQuestLog.Category.QUEST_PROGRESS, "Error granting collection reward node {}: {}",
                        node.getRewardNodeId(), e.getMessage(), e);
            }
        }
    }
}
