package org.arcadia.arc_quest.quest.spec;

import org.arcadia.arc_quest.guide.spec.GuideMediaSpec;

public class CollectionContentBlockSpecData {
    public String blockId = "";
    public QuestTextSpec text = QuestTextSpec.literal("");
    public GuideMediaSpec media = null;
    public QuestTextSpec caption = QuestTextSpec.literal("");
    public String fit = "CONTAIN";
    public boolean zoomable = true;
    public String reveal = "DISCOVERED";
    public String revealStepId = "";
}
