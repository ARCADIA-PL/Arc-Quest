package org.arcadia.arc_quest.quest.spec;

import org.arcadia.arc_quest.condition.ConditionSpec;

import java.util.ArrayList;
import java.util.List;

public class TransitionSpec {
    public String targetPhaseId = "";
    public List<String> targetPhaseIds = new ArrayList<>();
    public ConditionSpec condition = null;
}
