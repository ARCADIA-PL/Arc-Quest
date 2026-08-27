package org.arcadia.arc_quest.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ArcQuestLogConfig {
    public static final String FILE_NAME = "arc_quest_log.toml";
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue CORE;
    public static final ForgeConfigSpec.BooleanValue QUEST;
    public static final ForgeConfigSpec.BooleanValue QUEST_PROGRESS;
    public static final ForgeConfigSpec.BooleanValue QUEST_NETWORK;
    public static final ForgeConfigSpec.BooleanValue QUEST_RELOAD;
    public static final ForgeConfigSpec.BooleanValue DIALOGUE;
    public static final ForgeConfigSpec.BooleanValue DIALOGUE_NETWORK;
    public static final ForgeConfigSpec.BooleanValue GUIDE;
    public static final ForgeConfigSpec.BooleanValue TRADE;
    public static final ForgeConfigSpec.BooleanValue GACHA;
    public static final ForgeConfigSpec.BooleanValue NPC;
    public static final ForgeConfigSpec.BooleanValue MARKER;
    public static final ForgeConfigSpec.BooleanValue HUD;
    public static final ForgeConfigSpec.BooleanValue RENDER;
    public static final ForgeConfigSpec.BooleanValue COMMAND;
    public static final ForgeConfigSpec.BooleanValue COMPAT;
    public static final ForgeConfigSpec.BooleanValue DATA;
    public static final ForgeConfigSpec.BooleanValue API;
    public static final ForgeConfigSpec.BooleanValue PERSISTENCE;
    public static final ForgeConfigSpec.BooleanValue WEBSOCKET;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Arc Quest categorized diagnostic logging. All categories are disabled by default.")
                .push("logging");
        CORE = define(builder, "core", "Core mod lifecycle and registration logs.");
        QUEST = define(builder, "quest", "Quest definition and runtime logs.");
        QUEST_PROGRESS = define(builder, "quest_progress", "Quest and objective progress logs.");
        QUEST_NETWORK = define(builder, "quest_network", "Quest synchronization and network logs.");
        QUEST_RELOAD = define(builder, "quest_reload", "Quest datapack reload and compilation logs.");
        DIALOGUE = define(builder, "dialogue", "Dialogue runtime and definition logs.");
        DIALOGUE_NETWORK = define(builder, "dialogue_network", "Dialogue synchronization and network logs.");
        GUIDE = define(builder, "guide", "Guide definition and progression logs.");
        TRADE = define(builder, "trade", "Trade and shop runtime logs.");
        GACHA = define(builder, "gacha", "Gacha runtime and registry logs.");
        NPC = define(builder, "npc", "NPC binding and runtime logs.");
        MARKER = define(builder, "marker", "Quest marker logs.");
        HUD = define(builder, "hud", "HUD and screen state logs.");
        RENDER = define(builder, "render", "Client rendering logs.");
        COMMAND = define(builder, "command", "Command execution and suggestion logs.");
        COMPAT = define(builder, "compat", "Compatibility integration logs.");
        DATA = define(builder, "data", "Data loading and validation logs.");
        API = define(builder, "api", "Public API and extension point logs.");
        PERSISTENCE = define(builder, "persistence", "Player state and persistence logs.");
        WEBSOCKET = define(builder, "websocket", "WebSocket editor integration logs.");
        builder.pop();
        SPEC = builder.build();
    }

    private ArcQuestLogConfig() {
    }

    private static ForgeConfigSpec.BooleanValue define(ForgeConfigSpec.Builder builder, String key, String comment) {
        return builder.comment(comment).define(key, false);
    }
}
