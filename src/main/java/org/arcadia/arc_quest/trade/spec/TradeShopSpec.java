package org.arcadia.arc_quest.trade.spec;

import org.arcadia.arc_quest.condition.ConditionSpec;
import org.arcadia.arc_quest.dialogue.spec.DialogueTextSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class TradeShopSpec {
    public String shopId = "";
    public DialogueTextSpec displayName = new DialogueTextSpec();
    public DialogueTextSpec description = null;
    public List<TradeCategorySpec> categories = new ArrayList<>();
    public LinkedHashMap<String, TradeEntrySpec> entries = new LinkedHashMap<>();
    public ConditionSpec openCondition = null;
    public boolean simpleMode = false;
    public int themeColor = 0xE0C860;
    public String openSound = "";
    public String closeSound = "";
}
