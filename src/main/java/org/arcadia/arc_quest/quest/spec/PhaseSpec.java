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
    /**
     * 支持三种写法：
     * 1) 原生 scene id：mymod:my_scene
     * 2) 任务阶段简写：arc_quest:quest_path/phase_path
     * 3) 标准任务阶段 scene id：arc_quest:quest_phase/quest_path/phase_path
     */
    public String intelSceneId = "";
    public String phaseStartSound = "";
    public String phaseCompleteSound = "";
    public ConditionSpec enterCondition = null;
    public boolean autoEnterByCondition = true;
    public boolean autoAdvanceOnComplete = true;
    public CollectionEntryConfigSpecData collectionEntryConfig = null;
    public QuestVisualSpec visualConfig = new QuestVisualSpec();
}
