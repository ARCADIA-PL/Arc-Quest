package org.arcadia.arc_quest.guide.spec.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.arcadia.arc_quest.guide.spec.GuideCategorySpec;

public final class GuideCategorySpecJsonReader {
    private static final Gson GSON = new GsonBuilder().create();

    private GuideCategorySpecJsonReader() {
    }

    public static GuideCategorySpec read(String json) {
        return read(JsonParser.parseString(json));
    }

    public static GuideCategorySpec read(JsonElement json) {
        GuideCategorySpec spec = GSON.fromJson(json, GuideCategorySpec.class);
        return spec == null ? new GuideCategorySpec() : spec;
    }
}
