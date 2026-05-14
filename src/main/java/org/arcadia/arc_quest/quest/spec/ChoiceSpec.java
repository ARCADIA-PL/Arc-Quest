package org.arcadia.arc_quest.quest.spec;

import org.arcadia.arc_quest.condition.ConditionSpec;

public class ChoiceSpec {
    public QuestTextSpec text = QuestTextSpec.literal("");
    public String flagToSet = "";
    public String targetPhaseId = "";
    public ConditionSpec visibleCondition = null;
}
