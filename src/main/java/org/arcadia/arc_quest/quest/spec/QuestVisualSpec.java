package org.arcadia.arc_quest.quest.spec;

import java.util.LinkedHashMap;
import java.util.Map;

public class QuestVisualSpec {
    public int themeColor = 0xFFFFFF;
    public Map<String, AssetSpec> splashes = new LinkedHashMap<>();
    public Map<String, AssetSpec> icons = new LinkedHashMap<>();

    public static class AssetSpec {
        public String texture = "";
        public float scale = 1.0f;
    }
}
