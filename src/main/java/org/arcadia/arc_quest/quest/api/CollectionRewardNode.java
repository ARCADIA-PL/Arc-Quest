package org.arcadia.arc_quest.quest.api;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

public final class CollectionRewardNode {

    private final String rewardNodeId;
    private final RewardScope scope;
    private final EntryRewardGrantMode grantMode;
    private final List<IReward> rewards;
    private final List<CollectionCompletionRule> unlockRules;
    @Nullable
    private final String ownerId;

    public CollectionRewardNode(String rewardNodeId,
                                RewardScope scope,
                                EntryRewardGrantMode grantMode,
                                List<IReward> rewards,
                                List<CollectionCompletionRule> unlockRules,
                                @Nullable String ownerId) {
        this.rewardNodeId = Objects.requireNonNull(rewardNodeId);
        this.scope = scope != null ? scope : RewardScope.ENTRY;
        this.grantMode = grantMode != null ? grantMode : EntryRewardGrantMode.AUTO;
        this.rewards = List.copyOf(rewards != null ? rewards : List.of());
        this.unlockRules = List.copyOf(unlockRules != null ? unlockRules : List.of());
        this.ownerId = ownerId;
    }

    public String getRewardNodeId() {
        return rewardNodeId;
    }

    public RewardScope getScope() {
        return scope;
    }

    public EntryRewardGrantMode getGrantMode() {
        return grantMode;
    }

    public List<IReward> getRewards() {
        return rewards;
    }

    public List<CollectionCompletionRule> getUnlockRules() {
        return unlockRules;
    }

    @Nullable
    public String getOwnerId() {
        return ownerId;
    }
}
