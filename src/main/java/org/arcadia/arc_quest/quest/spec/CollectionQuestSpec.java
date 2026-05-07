package org.arcadia.arc_quest.quest.spec;

import org.arcadia.arc_quest.quest.api.CollectionPresentationMode;
import org.arcadia.arc_quest.quest.api.TrackerPresentationMode;

import java.util.ArrayList;
import java.util.List;

public class CollectionQuestSpec {
    public List<CollectionCategorySpec> categories = new ArrayList<>();
    public List<CollectionCompletionRuleSpec> questCompletionRules = new ArrayList<>();
    public List<CollectionRewardNodeSpec> questRewardNodes = new ArrayList<>();
    public TrackerPresentationMode trackerMode = TrackerPresentationMode.SUMMARY;
    public CollectionPresentationMode journalMode = CollectionPresentationMode.GRID_WITH_DETAIL;
    public boolean revealAllEntriesByDefault = false;
    public boolean allowManualRewardClaim = true;
    public boolean showCategories = true;
}
