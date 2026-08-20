package org.arcadia.arc_quest.quest.spec.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.arcadia.arc_quest.quest.spec.QuestSpec;

public final class QuestSpecJsonReader {
    private static final Gson GSON = new GsonBuilder().create();

    private QuestSpecJsonReader() {
    }

    public static QuestSpec read(String json) {
        return read(JsonParser.parseString(json));
    }

    public static QuestSpec read(JsonElement json) {
        return GSON.fromJson(json, QuestSpec.class);
    }
}
