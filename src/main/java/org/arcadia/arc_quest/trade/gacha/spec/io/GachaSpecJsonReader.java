package org.arcadia.arc_quest.trade.gacha.spec.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.arcadia.arc_quest.trade.gacha.spec.GachaShopSpec;

public final class GachaSpecJsonReader {
    private static final Gson GSON = new GsonBuilder().create();

    private GachaSpecJsonReader() {
    }

    public static GachaShopSpec read(String json) {
        return read(JsonParser.parseString(json));
    }

    public static GachaShopSpec read(JsonElement json) {
        return GSON.fromJson(json, GachaShopSpec.class);
    }
}
