package org.arcadia.arc_quest.quest.api;

import java.util.List;
import java.util.Objects;

/** rewardId is stable and unique across all triggers of a shared entry. */
public record CollectionEntryRewardDefinition(String rewardId, CollectionEntryRewardTrigger trigger,
                                             EntryRewardGrantMode grantMode, List<IReward> rewards) {
    public CollectionEntryRewardDefinition {
        if (rewardId == null || rewardId.isBlank() || rewardId.length() > 128 || !rewardId.equals(rewardId.trim()))
            throw new IllegalArgumentException("Entry reward requires a stable rewardId of 1..128 characters");
        trigger = Objects.requireNonNull(trigger, "entry reward trigger");
        grantMode = Objects.requireNonNullElse(grantMode, EntryRewardGrantMode.MANUAL);
        rewards = List.copyOf(rewards == null ? List.of() : rewards);
    }
    public CollectionEntryRewardDefinition(String rewardId, CollectionEntryRewardTrigger trigger, List<IReward> rewards) {
        this(rewardId, trigger, EntryRewardGrantMode.MANUAL, rewards);
    }
}
