package org.arcadia.arc_quest.dialogue.spec.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.arcadia.arc_quest.dialogue.spec.DialogueSpec;

public final class DialogueSpecJsonWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private DialogueSpecJsonWriter() {
    }

    public static String write(DialogueSpec spec) {
        return GSON.toJson(spec);
    }
}