package org.arcadia.arc_quest.guide.spec.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import org.arcadia.arc_quest.guide.spec.GuideCategorySpec;

import java.io.StringReader;

public final class GuideCategorySpecJsonReader {
    private static final Gson GSON = new GsonBuilder().create();

    private GuideCategorySpecJsonReader() {
    }

    public static GuideCategorySpec read(String json) {
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.setLenient(true);
        GuideCategorySpec spec = GSON.fromJson(reader, GuideCategorySpec.class);
        return spec == null ? new GuideCategorySpec() : spec;
    }
}
