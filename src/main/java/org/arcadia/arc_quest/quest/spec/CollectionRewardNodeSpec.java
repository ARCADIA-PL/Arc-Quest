package org.arcadia.arc_quest.quest.spec;

import org.arcadia.arc_quest.quest.api.EntryRewardGrantMode;
import org.arcadia.arc_quest.quest.api.RewardScope;

import java.util.ArrayList;
import java.util.List;

public class CollectionRewardNodeSpec {
    public String rewardNodeId = "";
    public RewardScope scope = RewardScope.ENTRY;
    public EntryRewardGrantMode grantMode = EntryRewardGrantMode.AUTO;
    public List<RewardSpec> rewards = new ArrayList<>();
    public List<CollectionCompletionRuleSpec> unlockRules = new ArrayList<>();
    public String ownerId = "";
}
