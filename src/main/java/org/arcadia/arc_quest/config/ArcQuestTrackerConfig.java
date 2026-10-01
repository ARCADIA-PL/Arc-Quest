package org.arcadia.arc_quest.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.arcadia.arc_quest.client.hud.quest.tracker.TrackerLayout;

/** Local HUD placement; never changes a player's tracked quest or server progress. */
public final class ArcQuestTrackerConfig {
    public static final String FILE_NAME = "arc_quest-tracker.toml";
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue POSITION_X;
    public static final ForgeConfigSpec.DoubleValue POSITION_Y;
    public static final ForgeConfigSpec.DoubleValue SCALE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("quest_tracker");
        POSITION_X = builder.comment("Horizontal position in the available screen area: 0 = left, 1 = right.")
                .defineInRange("position_x", TrackerLayout.DEFAULT.x(), 0.0, 1.0);
        POSITION_Y = builder.comment("Vertical position in the available screen area: 0 = top, 1 = bottom.")
                .defineInRange("position_y", TrackerLayout.DEFAULT.y(), 0.0, 1.0);
        SCALE = builder.comment("Size multiplier; the tracker keeps its aspect ratio and fits within the screen.")
                .defineInRange("scale", TrackerLayout.DEFAULT.scale(), TrackerLayout.MIN_SCALE, TrackerLayout.MAX_SCALE);
        builder.pop();
        SPEC = builder.build();
    }

    private ArcQuestTrackerConfig() {}

    public static TrackerLayout.Settings layout() {
        return new TrackerLayout.Settings(POSITION_X.get(), POSITION_Y.get(), SCALE.get());
    }

    public static void saveLayout(TrackerLayout.Settings settings) {
        POSITION_X.set(settings.x());
        POSITION_Y.set(settings.y());
        SCALE.set(settings.scale());
        SPEC.save();
    }
}
