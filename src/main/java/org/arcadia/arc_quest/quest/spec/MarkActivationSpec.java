package org.arcadia.arc_quest.quest.spec;

import org.arcadia.arc_quest.questmarker.api.QuestMarkerType;

import java.util.LinkedHashMap;
import java.util.Map;

public class MarkActivationSpec {
    public String type = "always";
    public String flag = "";
    public String questId = "";
    public MarkActivationSpec left = null;
    public MarkActivationSpec right = null;
}
