package org.arcadia.arc_quest.dialogue.spec.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import org.arcadia.arc_quest.dialogue.spec.DialogueSpec;

import java.io.StringReader;

public final class DialogueSpecJsonReader {
    private static final Gson GSON = new GsonBuilder().create();

    private DialogueSpecJsonReader() {
    }

    public static DialogueSpec read(String json) {
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.setLenient(true);
        return GSON.fromJson(reader, DialogueSpec.class);
    }
}