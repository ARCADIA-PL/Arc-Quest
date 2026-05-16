package org.arcadia.arc_quest.trade.gacha.spec.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.arcadia.arc_quest.trade.gacha.spec.GachaShopSpec;

public final class GachaSpecJsonReader {
    private static final Gson GSON = new GsonBuilder().create();

    private GachaSpecJsonReader() {
    }

    public static GachaShopSpec read(String json) {
        return GSON.fromJson(json, GachaShopSpec.class);
    }
}
