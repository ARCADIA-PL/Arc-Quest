package org.arcadia.arc_quest.npc.spec.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import org.arcadia.arc_quest.npc.spec.NpcSpec;

import java.io.StringReader;

public final class NpcSpecJsonReader {
    private static final Gson GSON = new GsonBuilder().create();

    private NpcSpecJsonReader() {
    }

    public static NpcSpec read(String json) {
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.setLenient(true);
        return GSON.fromJson(reader, NpcSpec.class);
    }
}