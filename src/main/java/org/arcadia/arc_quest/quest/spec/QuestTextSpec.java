package org.arcadia.arc_quest.quest.spec;

import java.util.ArrayList;
import java.util.List;

public class QuestTextSpec {
    public String mode = "literal";
    public String value = "";
    public List<String> args = new ArrayList<>();

    public static QuestTextSpec literal(String value) {
        QuestTextSpec spec = new QuestTextSpec();
        spec.mode = "literal";
        spec.value = value == null ? "" : value;
        return spec;
    }

    public static QuestTextSpec translatable(String key) {
        QuestTextSpec spec = new QuestTextSpec();
        spec.mode = "translatable";
        spec.value = key == null ? "" : key;
        return spec;
    }
}
