package org.arcadia.arc_quest.quest.logic.profile.collection;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.data.CollectionRuntimeData;

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
        for (IReward reward : node.getRewards()) {
            if (reward != null) reward.grant(player);
        }
        collectionData.markRewardClaimed(node.getRewardNodeId());
    }
}
