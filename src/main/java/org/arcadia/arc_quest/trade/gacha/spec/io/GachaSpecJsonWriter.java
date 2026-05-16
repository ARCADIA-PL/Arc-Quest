package org.arcadia.arc_quest.trade.gacha.spec.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.arcadia.arc_quest.trade.gacha.spec.GachaShopSpec;

public final class GachaSpecJsonWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private GachaSpecJsonWriter() {
    }

    public static String write(GachaShopSpec spec) {
        return GSON.toJson(spec);
    }
}
