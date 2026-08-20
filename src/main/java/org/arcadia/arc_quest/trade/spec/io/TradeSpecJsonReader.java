package org.arcadia.arc_quest.trade.spec.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.arcadia.arc_quest.trade.spec.TradeShopSpec;

public final class TradeSpecJsonReader {
    private static final Gson GSON = new GsonBuilder().create();

    private TradeSpecJsonReader() {
    }

    public static TradeShopSpec read(String json) {
        return read(JsonParser.parseString(json));
    }

    public static TradeShopSpec read(JsonElement json) {
        return GSON.fromJson(json, TradeShopSpec.class);
    }
}
