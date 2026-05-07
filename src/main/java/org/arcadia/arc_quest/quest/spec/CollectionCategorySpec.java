package org.arcadia.arc_quest.quest.spec;

import java.util.ArrayList;
import java.util.List;

public class CollectionCategorySpec {
    public String categoryId = "";
    public QuestTextSpec displayName = QuestTextSpec.literal("");
    public String iconTexture = "";
    public int sortOrder = 0;
    public List<CollectionCompletionRuleSpec> completionRules = new ArrayList<>();
    public List<CollectionRewardNodeSpec> rewardNodes = new ArrayList<>();
    public List<ConditionSpec> visibilityConditions = new ArrayList<>();
}
