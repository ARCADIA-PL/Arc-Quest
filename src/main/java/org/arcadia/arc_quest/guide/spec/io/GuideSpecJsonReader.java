package org.arcadia.arc_quest.guide.spec.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.arcadia.arc_quest.guide.spec.GuideSpec;

public final class GuideSpecJsonReader {
    private static final Gson GSON = new GsonBuilder().create();

    private GuideSpecJsonReader() {
    }

    public static GuideSpec read(String json) {
        return read(JsonParser.parseString(json));
    }

    public static GuideSpec read(JsonElement json) {
        GuideSpec spec = GSON.fromJson(json, GuideSpec.class);
        return spec == null ? new GuideSpec() : spec;
    }
}
