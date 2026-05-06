package org.arcadia.arc_quest.quest.spec.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.arcadia.arc_quest.quest.spec.QuestSpec;

public final class QuestSpecJsonWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private QuestSpecJsonWriter() {
    }

    public static String write(QuestSpec spec) {
        return GSON.toJson(spec);
    }
}
