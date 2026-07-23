package org.arcadia.arc_quest.quest.spec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ObjectiveSpec {
    public String id = "";
    public String type = "arc_quest:custom";
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
