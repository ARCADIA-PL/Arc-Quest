package org.arcadia.arc_quest.quest.spec;

import org.arcadia.arc_quest.condition.ConditionSpec;
import org.arcadia.arc_quest.quest.api.*;

import java.util.ArrayList;
import java.util.List;

public class QuestSpec {
    public String id = "";
    public String category = "arc_quest:adventure";
    public QuestTextSpec displayName = QuestTextSpec.literal("");
    public QuestTextSpec description = QuestTextSpec.literal("");
    public String iconTexture = "";
    public int sortOrder = 0;
    public boolean repeatable = false;
    public boolean allowAbandon = true;
    public QuestMode mode = QuestMode.PROGRESSION;
    public CollectionQuestSpecData collectionConfig = null;
    public String initialPhaseId = "";
    public List<String> initialPhaseIds = new ArrayList<>();
    public List<PhaseSpec> phases = new ArrayList<>();
    public List<ConditionSpec> unlockConditions = new ArrayList<>();
    public List<RewardSpec> completionRewards = new ArrayList<>();
    public List<String> flagsToSetOnAccept = new ArrayList<>();
    public List<String> flagsToSetOnComplete = new ArrayList<>();
    public List<MarkSpecData> relatedMarks = new ArrayList<>();
    public String chapterShopId = "";
    public ChapterShopType chapterShopType = ChapterShopType.TRADE;
    public boolean chapterShopPersistent = false;
    public QuestCompletionPolicy completionPolicy = QuestCompletionPolicy.ALL;
    public int completionRequiredCount = 0;
    public String completionTargetPhaseId = "";
    public QuestTimeLimitType timeLimitType = null;
    public long timeLimitValue = 0L;
    public String chapterStartSound = "";
    public String chapterFailSound = "";
    public String chapterCompleteSound = "";
    public QuestVisualSpec visualConfig = new QuestVisualSpec();
}
