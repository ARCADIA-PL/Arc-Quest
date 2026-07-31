package org.arcadia.arc_quest.trade.gacha.spec;

import org.arcadia.arc_quest.trade.spec.TradeCategorySpec;
import org.arcadia.arc_quest.condition.ConditionSpec;
import org.arcadia.arc_quest.dialogue.spec.DialogueTextSpec;
import org.arcadia.arc_quest.trade.spec.TradeOfferSpec;

import java.util.ArrayList;
import java.util.List;

public class GachaShopSpec {
    public String shopId = "";
    public DialogueTextSpec displayName = new DialogueTextSpec();
    public DialogueTextSpec description = null;
    public List<TradeCategorySpec> categories = new ArrayList<>();
    public ConditionSpec openCondition = null;
    public int themeColor = 0xFFD700;
    public boolean simpleMode = false;
    public String openSound = "";
    public String closeSound = "";

    public TradeOfferSpec drawCost = null;
    public List<TradeOfferSpec> drawCosts = new ArrayList<>();
    public int maxDraws = -1;
    public String cooldownType = "NONE";
    public long cooldownValue = 0;
    public int resetTimeTicks = 0;
    public ConditionSpec drawCondition = null;
    public ConditionSpec resetCondition = null;
    public boolean resetOnLimitReached = true;
    public boolean resetPityOnEarlyTrigger = true;
    public String drawCooldownSound = "";
    public String drawLimitReachedSound = "";
    public String drawConditionFailSound = "";
    public String drawFailSound = "";

    public List<GachaRaritySpec> rarities = new ArrayList<>();
    public PityConfigSpec pity = null;
    public List<GachaPoolSpec> pools = new ArrayList<>();
}
