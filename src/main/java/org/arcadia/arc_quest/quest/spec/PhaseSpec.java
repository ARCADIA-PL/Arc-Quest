package org.arcadia.arc_quest.quest.spec;

import java.util.ArrayList;
import java.util.List;

public class PhaseSpec {
    public String phaseId = "";
    public QuestTextSpec displayName = QuestTextSpec.literal("");
    public QuestTextSpec description = QuestTextSpec.literal("");
    public QuestTextSpec story = QuestTextSpec.literal("");
    public List<ObjectiveSpec> objectives = new ArrayList<>();
    public List<TransitionSpec> transitions = new ArrayList<>();
    public List<ChoiceSpec> choices = new ArrayList<>();
    public List<RewardSpec> phaseRewards = new ArrayList<>();
    public List<String> flagsToSetOnEnter = new ArrayList<>();
    public List<String> flagsToSetOnComplete = new ArrayList<>();
    public List<MarkSpecData> relatedMarks = new ArrayList<>();
    public String tradeShopId = "";
    public String intelSceneId = "";
    public String phaseStartSound = "";
    public String phaseCompleteSound = "";
    public ConditionSpec enterCondition = null;
    public boolean autoEnterByCondition = true;
    public CollectionEntryConfigSpecData collectionEntryConfig = null;
    public QuestVisualSpec visualConfig = new QuestVisualSpec();
}
