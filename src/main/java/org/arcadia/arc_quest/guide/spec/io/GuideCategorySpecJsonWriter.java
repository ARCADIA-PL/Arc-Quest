package org.arcadia.arc_quest.guide.spec.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import org.arcadia.arc_quest.guide.spec.GuideCategorySpec;

public final class GuideCategorySpecJsonWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private GuideCategorySpecJsonWriter() {
    }

    public static JsonElement toJson(GuideCategorySpec spec) {
        return GSON.toJsonTree(spec);
    }

    public static String write(GuideCategorySpec spec) {
        return GSON.toJson(spec);
    }
}
