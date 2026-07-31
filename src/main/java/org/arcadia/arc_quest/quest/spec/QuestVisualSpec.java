package org.arcadia.arc_quest.quest.spec;

import java.util.LinkedHashMap;
import java.util.Map;

public class QuestVisualSpec {
    public int themeColor = 0xFFFFFF;
    public Boolean useQuestSplashPresentation;
    public Map<String, AssetSpec> splashes = new LinkedHashMap<>();
    public Map<String, AssetSpec> icons = new LinkedHashMap<>();

    public static class AssetSpec {
        public String texture = "";
        public String itemId = "";
        public int itemCount = 1;
        public float scale = 1.0f;
        public float offsetX = 0f;
        public float offsetY = 0f;
        public int tintColor = 0xFFFFFFFF;
        public boolean enabled = true;
    }
}
