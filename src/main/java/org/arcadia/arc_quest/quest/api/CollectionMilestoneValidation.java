package org.arcadia.arc_quest.quest.api;

import net.minecraft.resources.ResourceLocation;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Shared constructor/Builder guard for milestones of modern collection quests. */
final class CollectionMilestoneValidation {
    private CollectionMilestoneValidation() {}
    static void validate(ResourceLocation questId, CollectionQuestConfig config) {
        if (config == null) return;
        Set<String> ids = new HashSet<>();
        validateNodes(config.getQuestRewardNodes(), RewardScope.QUEST, questId.toString(), ids);
        for (CollectionCategoryDefinition category : config.getCategories())
            validateNodes(category.getRewardNodes(), RewardScope.CATEGORY, category.getCategoryId(), ids);
    }
    private static void validateNodes(List<CollectionRewardNode> nodes, RewardScope scope, String owner, Set<String> ids) {
        for (CollectionRewardNode node : nodes) {
            String id = node.getRewardNodeId();
            if (id.isBlank()) throw new IllegalArgumentException("Collection reward node requires an explicit stable nodeId");
            if (!ids.add(id)) throw new IllegalArgumentException("Duplicate collection reward nodeId across quest scopes: " + id);
            if (node.getScope() != scope) throw new IllegalArgumentException("Collection reward node " + id + " requires scope " + scope);
            if (node.getOwnerId() != null && !node.getOwnerId().equals(owner))
                throw new IllegalArgumentException("Collection reward node " + id + " owner must match " + owner);
        }
    }
}
