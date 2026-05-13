package org.arcadia.arc_quest.dialogue.spec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DialogueNodeSpec {
    public String nodeId = "";
    public DialogueTextSpec speaker = new DialogueTextSpec();
    public DialogueTextSpec text = new DialogueTextSpec();
    public Map<String, ConditionalSaySpec> conditionalTexts = new LinkedHashMap<>();
    public List<DialogueChoiceSpec> choices = new ArrayList<>();
    public String autoNextId = "";
    public int delayMs = 0;
    public boolean repeatable = true;
    public long cooldownSeconds = 0;
    public String cooldownType = "NONE";
    public int resetTimeTicks = 0;
    public String nodeEnterSound = "";
}