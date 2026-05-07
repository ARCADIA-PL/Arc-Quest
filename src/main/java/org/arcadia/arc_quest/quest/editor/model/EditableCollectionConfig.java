package org.arcadia.arc_quest.quest.editor.model;

import org.arcadia.arc_quest.quest.api.CollectionPresentationMode;
import org.arcadia.arc_quest.quest.api.TrackerPresentationMode;

import java.util.ArrayList;
import java.util.List;

public class EditableCollectionConfig {
    public List<EditableCollectionCategory> categories = new ArrayList<>();
    public List<EditableCollectionCompletionRule> questCompletionRules = new ArrayList<>();
    public List<EditableCollectionRewardNode> questRewardNodes = new ArrayList<>();
    public TrackerPresentationMode trackerMode = TrackerPresentationMode.SUMMARY;
    public CollectionPresentationMode journalMode = CollectionPresentationMode.GRID_WITH_DETAIL;
    public boolean revealAllEntriesByDefault = false;
    public boolean allowManualRewardClaim = true;
    public boolean showCategories = true;
}
