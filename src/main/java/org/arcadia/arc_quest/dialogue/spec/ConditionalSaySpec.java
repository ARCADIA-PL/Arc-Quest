package org.arcadia.arc_quest.dialogue.spec;

import org.arcadia.arc_quest.condition.ConditionSpec;

import java.util.ArrayList;
import java.util.List;

public class ConditionalSaySpec {
    public String sayId = "";
    public DialogueTextSpec text = new DialogueTextSpec();
    public String soundEvent = "";
    public List<ConditionSpec> conditions = new ArrayList<>();
    public int priority = 0;
}