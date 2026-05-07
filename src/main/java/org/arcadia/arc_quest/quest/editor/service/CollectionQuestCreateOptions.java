package org.arcadia.arc_quest.quest.editor.service;

import org.arcadia.arc_quest.quest.api.CollectionPresentationMode;
import org.arcadia.arc_quest.quest.api.TrackerPresentationMode;

public class CollectionQuestCreateOptions {
    public boolean showCategories = true;
    public boolean revealAllEntriesByDefault = false;
    public boolean allowManualRewardClaim = true;
    public TrackerPresentationMode trackerMode = TrackerPresentationMode.SUMMARY;
    public CollectionPresentationMode journalMode = CollectionPresentationMode.GRID_WITH_DETAIL;
    public String defaultCategoryId = "default";
    public String defaultEntryId = "entry_1";
}
