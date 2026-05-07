package org.arcadia.arc_quest.quest.spec;

import org.arcadia.arc_quest.quest.api.CountingMode;
import org.arcadia.arc_quest.quest.api.EntryRewardGrantMode;
import org.arcadia.arc_quest.quest.api.HiddenPresentationMode;
import org.arcadia.arc_quest.quest.api.VisibilityMode;

import java.util.ArrayList;
import java.util.List;

public class CollectionEntrySpec {
    public String entryId = "";
    public String categoryId = "";
    public VisibilityMode visibilityMode = VisibilityMode.VISIBLE_BY_DEFAULT;
    public HiddenPresentationMode hiddenPresentationMode = HiddenPresentationMode.FULLY_HIDDEN;
    public List<ConditionSpec> visibilityConditions = new ArrayList<>();
    public CountingMode countingMode = CountingMode.BINARY;
    public int completionTarget = 1;
    public boolean repeatableProgress = false;
    public boolean repeatableCompletion = false;
    public int maxCount = 1;
    public EntryRewardGrantMode rewardGrantMode = EntryRewardGrantMode.AUTO;
    public List<CollectionRewardNodeSpec> rewardNodes = new ArrayList<>();
    public int sortOrder = 0;
    public boolean showInTrackerByDefault = true;
}
