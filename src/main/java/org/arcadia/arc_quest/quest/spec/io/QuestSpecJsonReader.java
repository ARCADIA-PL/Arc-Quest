package org.arcadia.arc_quest.quest.spec.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import org.arcadia.arc_quest.quest.spec.QuestSpec;

import java.io.StringReader;

public final class QuestSpecJsonReader {
    private static final Gson GSON = new GsonBuilder().create();

    private QuestSpecJsonReader() {
    }

    public static QuestSpec read(String json) {
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.setLenient(true);
        return GSON.fromJson(reader, QuestSpec.class);
    }
}
