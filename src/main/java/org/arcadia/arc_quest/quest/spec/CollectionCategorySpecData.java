package org.arcadia.arc_quest.quest.spec;

import java.util.ArrayList;
import java.util.List;

public class CollectionCategorySpecData {
    public String categoryId = "";
    public QuestTextSpec displayName = QuestTextSpec.literal("");
    public QuestTextSpec description = QuestTextSpec.literal("");
    public int sortOrder = 0;
    public List<ConditionSpec> completionRules = new ArrayList<>();
    public List<CollectionRewardNodeSpecData> rewardNodes = new ArrayList<>();
}
