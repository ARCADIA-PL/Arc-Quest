package org.arcadia.arc_quest.trade.gacha.spec;

import org.arcadia.arc_quest.dialogue.spec.DialogueTextSpec;

public class GachaItemSpec {
    public String itemId = "";
    public DialogueTextSpec displayName = new DialogueTextSpec();
    public String item = "";
    public int weight = 1;
    public String rarity = "RARE";
    public int minCount = 1;
    public int maxCount = 1;
    public int sortOrder = 0;
    public String rewardIcon = "";
    public int themeColor = -1;
    public String drawSuccessSound = "";
}
