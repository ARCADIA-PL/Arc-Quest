package org.arcadia.arc_quest.quest.editor.model;

import org.arcadia.arc_quest.quest.spec.ConditionSpec;
import org.arcadia.arc_quest.quest.spec.QuestTextSpec;

public class EditableChoice {
    public String choiceId = "";
    public QuestTextSpec text = QuestTextSpec.literal("");
    public String flagToSet = "";
    public ConditionSpec visibleCondition = null;
    public String targetPhaseNodeId = "";
}
