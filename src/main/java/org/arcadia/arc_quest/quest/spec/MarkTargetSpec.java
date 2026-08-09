package org.arcadia.arc_quest.quest.spec;

import java.util.LinkedHashMap;
import java.util.Map;

public class MarkTargetSpec {
    public String type = "pos";
    public String dimension = "";
    public Integer x = null;
    public Integer y = null;
    public Integer z = null;
    public String entityType = "";
    public String npcId = "";
    public Integer searchRadius = null;
    public String structureTag = "";
    public Integer structureSearchRadius = null;
    public boolean useSurfaceY = false;
    public String resolverId = "";
    public Map<String, String> args = new LinkedHashMap<>();
}
