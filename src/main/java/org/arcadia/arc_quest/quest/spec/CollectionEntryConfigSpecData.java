package org.arcadia.arc_quest.quest.spec;

import org.arcadia.arc_quest.condition.ConditionSpec;

import java.util.ArrayList;
import java.util.List;

public class CollectionEntryConfigSpecData {
    public String categoryId = "";
    public String visibilityMode = "VISIBLE_BY_DEFAULT";
    public String hiddenPresentationMode = "FULLY_HIDDEN";
    public List<ConditionSpec> visibilityConditions = new ArrayList<>();
    public String countingMode = "BINARY";
    public int completionTarget = 1;
    public boolean repeatableProgress = false;
    public boolean repeatableCompletion = false;
    public int maxCount = 0;
    public String rewardGrantMode = "AUTO";
    public List<CollectionRewardNodeSpecData> rewardNodes = new ArrayList<>();
    public int sortOrder = 0;
    public boolean showInTrackerByDefault = true;
}
