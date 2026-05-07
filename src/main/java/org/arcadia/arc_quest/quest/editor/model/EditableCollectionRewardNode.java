package org.arcadia.arc_quest.quest.editor.model;

import org.arcadia.arc_quest.quest.api.EntryRewardGrantMode;
import org.arcadia.arc_quest.quest.api.RewardScope;
import org.arcadia.arc_quest.quest.spec.RewardSpec;

import java.util.ArrayList;
import java.util.List;

public class EditableCollectionRewardNode {
    public String rewardNodeId = "";
    public RewardScope scope = RewardScope.ENTRY;
    public EntryRewardGrantMode grantMode = EntryRewardGrantMode.AUTO;
    public List<RewardSpec> rewards = new ArrayList<>();
    public List<EditableCollectionCompletionRule> unlockRules = new ArrayList<>();
    public String ownerId = "";
}
