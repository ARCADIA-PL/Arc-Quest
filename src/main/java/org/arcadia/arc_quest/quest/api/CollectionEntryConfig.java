package org.arcadia.arc_quest.quest.api;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

public final class CollectionEntryConfig {

    private final String categoryId;
    private final VisibilityMode visibilityMode;
    private final HiddenPresentationMode hiddenPresentationMode;
    private final List<ICondition> visibilityConditions;
    private final CountingMode countingMode;
    private final int completionTarget;
    private final boolean repeatableProgress;
    private final boolean repeatableCompletion;
    private final int maxCount;
    private final EntryRewardGrantMode rewardGrantMode;
    private final List<CollectionRewardNode> rewardNodes;
    private final int sortOrder;
    private final boolean showInTrackerByDefault;

    public CollectionEntryConfig(String categoryId,
                                 @Nullable VisibilityMode visibilityMode,
                                 @Nullable HiddenPresentationMode hiddenPresentationMode,
                                 List<ICondition> visibilityConditions,
                                 @Nullable CountingMode countingMode,
                                 int completionTarget,
                                 boolean repeatableProgress,
                                 boolean repeatableCompletion,
                                 int maxCount,
                                 @Nullable EntryRewardGrantMode rewardGrantMode,
                                 List<CollectionRewardNode> rewardNodes,
                                 int sortOrder,
                                 boolean showInTrackerByDefault) {
        this.categoryId = Objects.requireNonNull(categoryId);
        this.visibilityMode = visibilityMode != null ? visibilityMode : VisibilityMode.VISIBLE_BY_DEFAULT;
        this.hiddenPresentationMode = hiddenPresentationMode != null ? hiddenPresentationMode : HiddenPresentationMode.FULLY_HIDDEN;
        this.visibilityConditions = List.copyOf(visibilityConditions != null ? visibilityConditions : List.of());
        this.countingMode = countingMode != null ? countingMode : CountingMode.BINARY;
        this.completionTarget = Math.max(0, completionTarget);
        this.repeatableProgress = repeatableProgress;
        this.repeatableCompletion = repeatableCompletion;
        this.maxCount = Math.max(0, maxCount);
        this.rewardGrantMode = rewardGrantMode != null ? rewardGrantMode : EntryRewardGrantMode.AUTO;
        this.rewardNodes = List.copyOf(rewardNodes != null ? rewardNodes : List.of());
        this.sortOrder = sortOrder;
        this.showInTrackerByDefault = showInTrackerByDefault;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public VisibilityMode getVisibilityMode() {
        return visibilityMode;
    }

    public HiddenPresentationMode getHiddenPresentationMode() {
        return hiddenPresentationMode;
    }

    public List<ICondition> getVisibilityConditions() {
        return visibilityConditions;
    }

    public CountingMode getCountingMode() {
        return countingMode;
    }

    public int getCompletionTarget() {
        return completionTarget;
    }

    public boolean isRepeatableProgress() {
        return repeatableProgress;
    }

    public boolean isRepeatableCompletion() {
        return repeatableCompletion;
    }

    public int getMaxCount() {
        return maxCount;
    }

    public EntryRewardGrantMode getRewardGrantMode() {
        return rewardGrantMode;
    }

    public List<CollectionRewardNode> getRewardNodes() {
        return rewardNodes;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isShowInTrackerByDefault() {
        return showInTrackerByDefault;
    }
}
