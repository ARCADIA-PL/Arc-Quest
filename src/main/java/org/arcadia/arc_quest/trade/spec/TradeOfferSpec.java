package org.arcadia.arc_quest.trade.spec;

import java.util.ArrayList;
import java.util.List;

public class TradeOfferSpec {
    public String type = "item";

    public String itemId = "";
    public int count = 1;
    public String nbt = "";

    public String command = "";
    public String executeAs = "console";

    public String effectId = "";
    public int duration = 0;
    public int amplifier = 0;

    public String flagName = "";

    public List<TradeOfferSpec> offers = new ArrayList<>();
}
