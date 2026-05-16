package org.arcadia.arc_quest.trade.spec;

import org.arcadia.arc_quest.condition.ConditionSpec;
import org.arcadia.arc_quest.dialogue.spec.DialogueTextSpec;

import java.util.ArrayList;
import java.util.List;

public class TradeEntrySpec {
    public String entryId = "";
    public DialogueTextSpec displayName = new DialogueTextSpec();
    public DialogueTextSpec description = null;
    public List<TradeOfferSpec> costs = new ArrayList<>();
    public List<TradeOfferSpec> rewards = new ArrayList<>();
    public String category = "";
    public ConditionSpec visibleCondition = null;
    public ConditionSpec canBuyCondition = null;
    public String cooldownType = "NONE";
    public long cooldownValue = 0;
    public int resetTimeTicks = 0;
    public int maxPurchases = 0;
    public String rewardIcon = "";
    public String costIcon = "";
    public int sortOrder = 0;
    public int themeColor = -1;
    public String purchaseSuccessSound = "";
    public String purchaseFailSound = "";
    public String cooldownSound = "";
    public String limitReachedSound = "";
    public String conditionFailSound = "";
}
