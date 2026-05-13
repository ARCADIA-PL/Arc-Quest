package org.arcadia.arc_quest.dialogue.spec;

import java.util.ArrayList;
import java.util.List;

public class DialogueConditionSpec {
    public String type = "always";

    public DialogueConditionSpec inner;
    public List<DialogueConditionSpec> conditions = new ArrayList<>();

    public String questId = "";
    public String phaseId = "";
    public String targetPhaseId = "";
    public String fromPhaseId = "";
    public String toPhaseId = "";

    public String flagName = "";
    public String variableKey = "";
    public String op = "EQ";
    public int value = 0;

    public int startTick = 0;
    public int endTick = 0;

    public String nodeId = "";
    public String choiceId = "";
    public String dialogueId = "";

    public long cooldownSeconds = 0;

    public String name = "";
}