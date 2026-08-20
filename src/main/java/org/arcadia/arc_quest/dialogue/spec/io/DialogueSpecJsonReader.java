package org.arcadia.arc_quest.dialogue.spec.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.arcadia.arc_quest.dialogue.spec.DialogueSpec;

public final class DialogueSpecJsonReader {
    private static final Gson GSON = new GsonBuilder().create();

    private DialogueSpecJsonReader() {
    }

    public static DialogueSpec read(String json) {
        return read(JsonParser.parseString(json));
    }

    public static DialogueSpec read(JsonElement json) {
        return GSON.fromJson(json, DialogueSpec.class);
    }
}
