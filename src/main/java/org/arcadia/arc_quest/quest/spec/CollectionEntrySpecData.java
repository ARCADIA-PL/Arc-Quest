package org.arcadia.arc_quest.quest.spec;

import org.arcadia.arc_quest.quest.api.icon.ObjectiveIconSpec;
import java.util.ArrayList;
import java.util.List;

/** Reusable knowledge definition, independent from PhaseSpec and task progress. */
public class CollectionEntrySpecData {
    public String entryId = "";
    public String categoryId = "";
    public QuestTextSpec displayName = QuestTextSpec.literal("");
    public QuestTextSpec description = QuestTextSpec.literal("");
    public String subjectKind = "CUSTOM";
    public String subjectId = "";
    public String itemTag = "";
    public ObjectiveIconSpec icon = ObjectiveIconSpec.AUTO;
    public List<CollectionContentBlockSpecData> content = new ArrayList<>();
    public List<String> relatedItems = new ArrayList<>();
    public List<ObjectiveSpec> discoveryObjectives = new ArrayList<>();
    public List<ObjectiveSpec> researchObjectives = new ArrayList<>();
    public List<CollectionEntryRewardSpecData> rewards = new ArrayList<>();
    public List<org.arcadia.arc_quest.condition.ConditionSpec> recordConditions = new ArrayList<>();
    public String visibilityMode = "VISIBLE_BY_DEFAULT";
    public String hiddenPresentationMode = "FULLY_HIDDEN";
    public int sortOrder = 0;
    public boolean researchAfterDiscovery = false;
}
