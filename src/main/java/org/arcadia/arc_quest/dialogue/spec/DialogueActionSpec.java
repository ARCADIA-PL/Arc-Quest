package org.arcadia.arc_quest.dialogue.spec;

import java.util.LinkedHashMap;
import java.util.Map;

public class DialogueActionSpec {
    public String type = "no_op";

    public String questId = "";
    public int amount = 0;
    public String itemId = "";
    public int count = 1;
    public String npcId = "";
    public String targetId = "";
    public String command = "";
    public String flagName = "";
    public String key = "";
    public int value = 0;
    public String shopId = "";
    public String restoreNodeId = "";
    public String customTypeId = "";
    public Map<String, Object> customData = new LinkedHashMap<>();
}