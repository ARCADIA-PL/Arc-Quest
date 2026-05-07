package org.arcadia.arc_quest.quest.editor.model;

import org.arcadia.arc_quest.quest.api.CollectionEntryConfig;
import org.arcadia.arc_quest.quest.api.CountingMode;
import org.arcadia.arc_quest.quest.api.EntryRewardGrantMode;
import org.arcadia.arc_quest.quest.api.HiddenPresentationMode;
import org.arcadia.arc_quest.quest.api.ICondition;
import org.arcadia.arc_quest.quest.api.VisibilityMode;

import java.util.ArrayList;
import java.util.List;

public class EditableCollectionEntry {
    public String entryId = "";
    public String ownerPhaseNodeId = "";
    public String objectiveId = "";
    public String categoryId = "";
    public VisibilityMode visibilityMode = VisibilityMode.VISIBLE_BY_DEFAULT;
    public HiddenPresentationMode hiddenPresentationMode = HiddenPresentationMode.FULLY_HIDDEN;
    public List<ICondition> visibilityConditions = new ArrayList<>();
    public CountingMode countingMode = CountingMode.BINARY;
    public int completionTarget = 1;
    public boolean repeatableProgress = false;
    public boolean repeatableCompletion = false;
    public int maxCount = 1;
    public EntryRewardGrantMode rewardGrantMode = EntryRewardGrantMode.AUTO;
    public List<EditableCollectionRewardNode> rewardNodes = new ArrayList<>();
    public int sortOrder = 0;
    public boolean showInTrackerByDefault = true;

    public static EditableCollectionEntry fromConfig(String ownerPhaseNodeId, String objectiveId, CollectionEntryConfig config) {
        EditableCollectionEntry entry = new EditableCollectionEntry();
        entry.ownerPhaseNodeId = ownerPhaseNodeId == null ? "" : ownerPhaseNodeId;
        entry.objectiveId = objectiveId == null ? "" : objectiveId;
        entry.entryId = entry.ownerPhaseNodeId + "::" + entry.objectiveId;
        if (config != null) {
            entry.categoryId = config.getCategoryId();
            entry.visibilityMode = config.getVisibilityMode();
            entry.hiddenPresentationMode = config.getHiddenPresentationMode();
            entry.visibilityConditions.addAll(config.getVisibilityConditions());
            entry.countingMode = config.getCountingMode();
            entry.completionTarget = config.getCompletionTarget();
            entry.repeatableProgress = config.isRepeatableProgress();
            entry.repeatableCompletion = config.isRepeatableCompletion();
            entry.maxCount = config.getMaxCount();
            entry.rewardGrantMode = config.getRewardGrantMode();
            entry.sortOrder = config.getSortOrder();
            entry.showInTrackerByDefault = config.isShowInTrackerByDefault();
        }
        return entry;
    }
}
