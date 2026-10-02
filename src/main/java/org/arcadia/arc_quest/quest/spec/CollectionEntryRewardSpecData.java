package org.arcadia.arc_quest.quest.spec;

import java.util.ArrayList;
import java.util.List;

public class CollectionEntryRewardSpecData {
    public String rewardId = "";
    public String trigger = "DISCOVERED";
    public String grantMode = "MANUAL";
    public List<RewardSpec> rewards = new ArrayList<>();
    public String outcomeId = "";
    public String previewVisibility = "UNLOCKED_ONLY";
}
