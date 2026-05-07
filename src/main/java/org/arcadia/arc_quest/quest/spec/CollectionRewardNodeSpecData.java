package org.arcadia.arc_quest.quest.spec;

import java.util.ArrayList;
import java.util.List;

public class CollectionRewardNodeSpecData {
    public String nodeId = "";
    public String scope = "QUEST";
    public String grantMode = "AUTO";
    public List<RewardSpec> rewards = new ArrayList<>();
    public List<ConditionSpec> completionRules = new ArrayList<>();
    public String scopeRefId = "";
}
