package org.arcadia.arc_quest.guide.spec.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import org.arcadia.arc_quest.guide.spec.GuideSpec;

import java.io.StringReader;

public final class GuideSpecJsonReader {
    private static final Gson GSON = new GsonBuilder().create();

    private GuideSpecJsonReader() {
    }

    public static GuideSpec read(String json) {
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.setLenient(true);
        GuideSpec spec = GSON.fromJson(reader, GuideSpec.class);
        return spec == null ? new GuideSpec() : spec;
    }
}
