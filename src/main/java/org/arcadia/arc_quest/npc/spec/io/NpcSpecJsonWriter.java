package org.arcadia.arc_quest.npc.spec.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import org.arcadia.arc_quest.npc.spec.NpcSpec;

public final class NpcSpecJsonWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private NpcSpecJsonWriter() {
    }

    public static JsonElement toJson(NpcSpec spec) {
        return GSON.toJsonTree(spec);
    }

    public static String write(NpcSpec spec) {
        return GSON.toJson(spec);
    }
}