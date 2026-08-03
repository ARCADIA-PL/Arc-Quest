package org.arcadia.arc_quest.dialogue.spec;

import org.arcadia.arc_quest.condition.ConditionSpec;
import org.arcadia.arc_quest.quest.spec.MarkSpecData;

import java.util.ArrayList;
import java.util.List;

public class DialogueChoiceSpec {
    public String choiceId = "";
    public DialogueTextSpec text = new DialogueTextSpec();
    public String nextNodeId = "";
    public List<ConditionSpec> conditions = new ArrayList<>();
    public List<DialogueActionSpec> actions = new ArrayList<>();
    public boolean repeatable = true;
    public long cooldownSeconds = 0;
    public String cooldownType = "NONE";
    public int resetTimeTicks = 0;
    public int priority = 0;
    public String restoreNodeId = "";
    public String selectSound = "";
    public List<MarkSpecData> relatedMarks = new ArrayList<>();
}
