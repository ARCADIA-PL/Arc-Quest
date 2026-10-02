package org.arcadia.arc_quest.quest.spec;

import org.arcadia.arc_quest.quest.api.icon.ObjectiveIconSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** Reusable knowledge definition, independent from PhaseSpec and task progress. */
public class CollectionEntrySpecData {
    public String entryId = "";
    public String categoryId = "";
    public QuestTextSpec displayName = QuestTextSpec.literal("");
    public QuestTextSpec description = QuestTextSpec.literal("");
    public QuestTextSpec publicClue = QuestTextSpec.literal("");
    public String subjectKind = "CUSTOM";
    public String subjectId = "";
    public String itemTag = "";
    /** Server-authorized presentation snapshot; null means use the currently loaded Tag. */
    public List<String> frozenTagMembers;
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
    /** Missing version means legacy data; new builders/exporters emit version 2 explicitly. */
    public int gameplayVersion = 1;
    public List<CollectionOutcomeSpecData> outcomes = new ArrayList<>();
    public Map<String, String> legacyResearchOutcomeMappings = new LinkedHashMap<>();
    public List<ObjectiveSpec> legacyResearchObjectives = new ArrayList<>();
}
