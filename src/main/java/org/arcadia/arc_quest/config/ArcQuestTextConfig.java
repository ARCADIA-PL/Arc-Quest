package org.arcadia.arc_quest.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ArcQuestTextConfig {
    public static final String FILE_NAME = "arc_quest-text.toml";
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue DIALOGUE_SCALE;
    public static final ModConfigSpec.DoubleValue JOURNAL_SCALE;
    public static final ModConfigSpec.DoubleValue GUIDE_SCALE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("text_scale");
        DIALOGUE_SCALE = define(builder, "dialogue", "Dialogue text scale.");
        JOURNAL_SCALE = define(builder, "quest_journal", "Quest journal text scale.");
        GUIDE_SCALE = define(builder, "guide", "Guide text scale.");
        builder.pop();
        SPEC = builder.build();
    }

    private ArcQuestTextConfig() {
    }

    private static ModConfigSpec.DoubleValue define(ModConfigSpec.Builder builder, String key, String comment) {
        return builder.comment(comment).defineInRange(key, 1.0, 0.75, 1.5);
    }

    public static double dialogueScale() {
        return DIALOGUE_SCALE.get();
    }

    public static double journalScale() {
        return JOURNAL_SCALE.get();
    }

    public static double guideScale() {
        return GUIDE_SCALE.get();
    }

    public static void save() {
        SPEC.save();
    }
}
