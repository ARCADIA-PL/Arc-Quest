package org.arcadia.arc_quest.quest.editor.model;

import org.arcadia.arc_quest.quest.spec.ConditionSpec;
import org.arcadia.arc_quest.quest.spec.QuestTextSpec;

import java.util.ArrayList;
import java.util.List;

public class EditableCollectionCategory {
    public String categoryId = "";
    public QuestTextSpec displayName = QuestTextSpec.literal("");
    public String iconTexture = "";
    public int sortOrder = 0;
    public List<EditableCollectionCompletionRule> completionRules = new ArrayList<>();
    public List<EditableCollectionRewardNode> rewardNodes = new ArrayList<>();
    public List<ConditionSpec> visibilityConditions = new ArrayList<>();
}
