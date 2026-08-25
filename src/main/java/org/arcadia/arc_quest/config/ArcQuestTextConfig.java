package org.arcadia.arc_quest.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ArcQuestTextConfig {
    public static final String FILE_NAME = "arc_quest-text.toml";
    public static final double MIN_SCALE = 0.5;
    public static final double MAX_SCALE = 2.0;
    public static final double DEFAULT_SCALE = 1.0;
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue DIALOGUE_SCALE;
    public static final ForgeConfigSpec.DoubleValue JOURNAL_SCALE;
    public static final ForgeConfigSpec.DoubleValue GUIDE_SCALE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("text_scale");
        DIALOGUE_SCALE = define(builder, "dialogue", "Dialogue text scale.");
        JOURNAL_SCALE = define(builder, "quest_journal", "Quest journal text scale.");
        GUIDE_SCALE = define(builder, "guide", "Guide text scale.");
        builder.pop();
        SPEC = builder.build();
    }

    private ArcQuestTextConfig() {
    }

    private static ForgeConfigSpec.DoubleValue define(ForgeConfigSpec.Builder builder, String key, String comment) {
        return builder.comment(comment).defineInRange(key, DEFAULT_SCALE, MIN_SCALE, MAX_SCALE);
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
