package org.arcadia.arc_quest.quest.api;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

public final class CollectionCategoryDefinition {

    private final String categoryId;
    private final QuestText displayName;
    @Nullable
    private final ResourceLocation iconTexture;
    private final int sortOrder;
    private final List<CollectionCompletionRule> completionRules;
    private final List<CollectionRewardNode> rewardNodes;
    private final List<ICondition> visibilityConditions;

    public CollectionCategoryDefinition(String categoryId,
                                        QuestText displayName,
                                        @Nullable ResourceLocation iconTexture,
                                        int sortOrder,
                                        List<CollectionCompletionRule> completionRules,
                                        List<CollectionRewardNode> rewardNodes,
                                        List<ICondition> visibilityConditions) {
        this.categoryId = Objects.requireNonNull(categoryId);
        this.displayName = Objects.requireNonNull(displayName);
        this.iconTexture = iconTexture;
        this.sortOrder = sortOrder;
        this.completionRules = List.copyOf(completionRules != null ? completionRules : List.of());
        this.rewardNodes = List.copyOf(rewardNodes != null ? rewardNodes : List.of());
        this.visibilityConditions = List.copyOf(visibilityConditions != null ? visibilityConditions : List.of());
    }

    public String getCategoryId() {
        return categoryId;
    }

    public QuestText getDisplayNameText() {
        return displayName;
    }

    @Nullable
    public ResourceLocation getIconTexture() {
        return iconTexture;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public List<CollectionCompletionRule> getCompletionRules() {
        return completionRules;
    }

    public List<CollectionRewardNode> getRewardNodes() {
        return rewardNodes;
    }

    public List<ICondition> getVisibilityConditions() {
        return visibilityConditions;
    }
}
