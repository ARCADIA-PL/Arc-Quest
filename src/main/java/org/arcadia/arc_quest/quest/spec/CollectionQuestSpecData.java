package org.arcadia.arc_quest.quest.spec;

import java.util.ArrayList;
import java.util.List;

public class CollectionQuestSpecData {
    public List<CollectionEntrySpecData> entries = new ArrayList<>();
    /** References to entries explicitly registered through CollectionEntryRegistry. */
    public List<String> entryIds = new ArrayList<>();
    public List<CollectionCategorySpecData> categories = new ArrayList<>();
    public List<ConditionSpec> completionRules = new ArrayList<>();
    public List<CollectionRewardNodeSpecData> rewardNodes = new ArrayList<>();
    public String trackerPresentationMode = "";
    public String collectionPresentationMode = "";
    public boolean allowCategoryCollapse = false;
    public boolean showCompletedEntries = true;
    public boolean showProgressInTracker = true;
}
