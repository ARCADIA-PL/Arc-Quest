package org.arcadia.arc_quest.quest.spec.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.arcadia.arc_quest.quest.spec.QuestSpec;

public final class QuestSpecJsonReader {
    private static final Gson GSON = new GsonBuilder().create();

    private QuestSpecJsonReader() {
    }

    public static QuestSpec read(String json) {
        return GSON.fromJson(json, QuestSpec.class);
    }
}
