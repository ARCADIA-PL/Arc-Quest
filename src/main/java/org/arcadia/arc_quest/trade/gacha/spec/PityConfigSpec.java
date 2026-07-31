package org.arcadia.arc_quest.trade.gacha.spec;

import org.arcadia.arc_quest.condition.ConditionSpec;

public class PityConfigSpec {
    public int threshold = 10;
    public String targetRarity = "LEGENDARY";
    public String guaranteedItemId = "";
    public boolean resetOnEarlyTrigger = true;
    public String resetCooldownType = "NONE";
    public int resetCooldownValue = 0;
    public ConditionSpec resetCondition = null;
    public boolean resetOnTrigger = true;
}
