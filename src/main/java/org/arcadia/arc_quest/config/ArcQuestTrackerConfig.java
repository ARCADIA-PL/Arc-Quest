package org.arcadia.arc_quest.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.arcadia.arc_quest.client.hud.quest.tracker.TrackerLayout;
import org.arcadia.arc_quest.client.hud.quest.tracker.TrackerStyle;

/** Local HUD placement; never changes a player's tracked quest or server progress. */
public final class ArcQuestTrackerConfig {
    public static final String FILE_NAME = "arc_quest-tracker.toml";
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.DoubleValue POSITION_X;
    public static final ModConfigSpec.DoubleValue POSITION_Y;
    public static final ModConfigSpec.DoubleValue SCALE;
    public static final ModConfigSpec.EnumValue<TrackerStyle> STYLE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("quest_tracker");
        ENABLED = builder.comment("Show the quest tracker HUD; hiding it keeps the tracked quest and progress.")
                .define("enabled", true);
        POSITION_X = builder.comment("Horizontal position in the available screen area: 0 = left, 1 = right.")
                .defineInRange("position_x", TrackerLayout.DEFAULT.x(), 0.0, 1.0);
        POSITION_Y = builder.comment("Vertical position in the available screen area: 0 = top, 1 = bottom.")
                .defineInRange("position_y", TrackerLayout.DEFAULT.y(), 0.0, 1.0);
        SCALE = builder.comment("Size multiplier; the tracker keeps its aspect ratio and fits within the screen.")
                .defineInRange("scale", TrackerLayout.DEFAULT.scale(), TrackerLayout.MIN_SCALE, TrackerLayout.MAX_SCALE);
        STYLE = builder.comment("CLASSIC: detailed list; FOCUS: next unfinished goals; OVERVIEW: parallel phase progress.")
                .defineEnum("style", TrackerStyle.CLASSIC);
        builder.pop();
        SPEC = builder.build();
    }

    private ArcQuestTrackerConfig() {}

    public static boolean enabled() { return ENABLED.get(); }

    public static void setEnabled(boolean enabled) {
        ENABLED.set(enabled);
        SPEC.save();
    }

    public static TrackerLayout.Settings layout() {
        return new TrackerLayout.Settings(POSITION_X.get(), POSITION_Y.get(), SCALE.get());
    }

    public static void saveLayout(TrackerLayout.Settings settings) {
        save(settings, style());
    }

    public static TrackerStyle style() {
        return STYLE.get();
    }

    /** Persist the editor draft together, with one config save. */
    public static void save(TrackerLayout.Settings settings, TrackerStyle style) {
        POSITION_X.set(settings.x());
        POSITION_Y.set(settings.y());
        SCALE.set(settings.scale());
        STYLE.set(style == null ? TrackerStyle.CLASSIC : style);
        SPEC.save();
    }
}
