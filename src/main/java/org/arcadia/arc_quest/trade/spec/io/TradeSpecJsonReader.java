package org.arcadia.arc_quest.trade.spec.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import org.arcadia.arc_quest.trade.spec.TradeShopSpec;

import java.io.StringReader;

public final class TradeSpecJsonReader {
    private static final Gson GSON = new GsonBuilder().create();

    private TradeSpecJsonReader() {
    }

    public static TradeShopSpec read(String json) {
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.setLenient(true);
        return GSON.fromJson(reader, TradeShopSpec.class);
    }
}
