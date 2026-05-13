package org.arcadia.arc_quest.dialogue.spec;

import java.util.ArrayList;
import java.util.List;

public class DialogueTextSpec {
    public String mode = "literal";
    public String value = "";
    public List<String> args = new ArrayList<>();

    public static DialogueTextSpec literal(String value) {
        DialogueTextSpec spec = new DialogueTextSpec();
        spec.mode = "literal";
        spec.value = value != null ? value : "";
        return spec;
    }

    public static DialogueTextSpec translatable(String key) {
        DialogueTextSpec spec = new DialogueTextSpec();
        spec.mode = "translatable";
        spec.value = key != null ? key : "";
        return spec;
    }
}