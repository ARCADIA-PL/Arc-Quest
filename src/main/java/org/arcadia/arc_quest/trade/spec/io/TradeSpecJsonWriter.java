package org.arcadia.arc_quest.trade.spec.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import org.arcadia.arc_quest.trade.spec.TradeShopSpec;

public final class TradeSpecJsonWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private TradeSpecJsonWriter() {
    }

    public static JsonElement toJson(TradeShopSpec spec) {
        return GSON.toJsonTree(spec);
    }

    public static String write(TradeShopSpec spec) {
        return GSON.toJson(spec);
    }
}
