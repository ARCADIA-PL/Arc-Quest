package org.arcadia.arc_quest.quest.editor.model;

import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.arcadia.arc_quest.quest.spec.MarkSpecData;
import org.arcadia.arc_quest.quest.spec.QuestTextSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EditableObjective {
    public String objectiveId = "";
    public ObjectiveType type = ObjectiveType.CUSTOM;
    public String targetId = "";
    public int requiredCount = 1;
    public QuestTextSpec displayText = QuestTextSpec.literal("???");
    public boolean hidden = false;
    public boolean optional = false;
    public String npcId = "";
    public String itemTag = "";
    public Integer x = null;
    public Integer y = null;
    public Integer z = null;
    public Integer radius = null;
    public String countMode = "";
    public Integer countBase = null;
    public Integer countPerLevel = null;
    public Integer countMin = null;
    public Integer countMax = null;
    public List<MarkSpecData> relatedMarks = new ArrayList<>();
    public Map<String, String> extraData = new LinkedHashMap<>();
}
