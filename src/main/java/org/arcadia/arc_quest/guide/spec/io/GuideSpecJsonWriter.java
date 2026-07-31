package org.arcadia.arc_quest.guide.spec.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import org.arcadia.arc_quest.guide.spec.GuideSpec;

public final class GuideSpecJsonWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private GuideSpecJsonWriter() {
    }

    public static JsonElement toJson(GuideSpec spec) {
        return GSON.toJsonTree(spec);
    }

    public static String write(GuideSpec spec) {
        return GSON.toJson(spec);
    }
}
