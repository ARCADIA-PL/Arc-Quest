package org.arcadia.arc_quest.npc.spec.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.arcadia.arc_quest.npc.spec.NpcSpec;

public final class NpcSpecJsonReader {
    private static final Gson GSON = new GsonBuilder().create();

    private NpcSpecJsonReader() {
    }

    public static NpcSpec read(String json) {
        return read(JsonParser.parseString(json));
    }

    public static NpcSpec read(JsonElement json) {
        return GSON.fromJson(json, NpcSpec.class);
    }
}
