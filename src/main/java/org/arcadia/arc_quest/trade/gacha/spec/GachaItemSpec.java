package org.arcadia.arc_quest.trade.gacha.spec;

import org.arcadia.arc_quest.dialogue.spec.DialogueTextSpec;
import org.arcadia.arc_quest.condition.ConditionSpec;
import org.arcadia.arc_quest.trade.spec.TradeOfferSpec;

import java.util.ArrayList;
import java.util.List;

public class GachaItemSpec {
    public String itemId = "";
    public DialogueTextSpec displayName = new DialogueTextSpec();
    public String item = "";
    public int weight = 1;
    public boolean countsTowardsPity = true;
    public String rarity = "RARE";
    public int minCount = 1;
    public int maxCount = 1;
    public int sortOrder = 0;
    public String rewardIcon = "";
    public int themeColor = -1;
    public String drawSuccessSound = "";
    public ConditionSpec visibleCondition = null;
    public TradeOfferSpec reward = null;
    public List<GachaWeightModifierSpec> weightModifiers = new ArrayList<>();
}
