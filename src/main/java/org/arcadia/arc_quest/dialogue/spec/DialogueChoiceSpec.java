package org.arcadia.arc_quest.dialogue.spec;

import java.util.ArrayList;
import java.util.List;

public class DialogueChoiceSpec {
    public String choiceId = "";
    public DialogueTextSpec text = new DialogueTextSpec();
    public String nextNodeId = "";
    public List<DialogueConditionSpec> conditions = new ArrayList<>();
    public List<DialogueActionSpec> actions = new ArrayList<>();
    public boolean repeatable = true;
    public long cooldownSeconds = 0;
    public String cooldownType = "NONE";
    public int resetTimeTicks = 0;
    public int priority = 0;
    public String restoreNodeId = "";
    public String selectSound = "";
}