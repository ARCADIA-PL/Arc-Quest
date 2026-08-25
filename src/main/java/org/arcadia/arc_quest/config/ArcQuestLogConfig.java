package org.arcadia.arc_quest.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ArcQuestLogConfig {
    public static final String FILE_NAME = "arc_quest_log.toml";
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue CORE;
    public static final ModConfigSpec.BooleanValue QUEST;
    public static final ModConfigSpec.BooleanValue QUEST_PROGRESS;
    public static final ModConfigSpec.BooleanValue QUEST_NETWORK;
    public static final ModConfigSpec.BooleanValue QUEST_RELOAD;
    public static final ModConfigSpec.BooleanValue DIALOGUE;
    public static final ModConfigSpec.BooleanValue DIALOGUE_NETWORK;
    public static final ModConfigSpec.BooleanValue GUIDE;
    public static final ModConfigSpec.BooleanValue TRADE;
    public static final ModConfigSpec.BooleanValue GACHA;
    public static final ModConfigSpec.BooleanValue NPC;
    public static final ModConfigSpec.BooleanValue MARKER;
    public static final ModConfigSpec.BooleanValue HUD;
    public static final ModConfigSpec.BooleanValue RENDER;
    public static final ModConfigSpec.BooleanValue COMMAND;
    public static final ModConfigSpec.BooleanValue COMPAT;
    public static final ModConfigSpec.BooleanValue DATA;
    public static final ModConfigSpec.BooleanValue API;
    public static final ModConfigSpec.BooleanValue PERSISTENCE;
    public static final ModConfigSpec.BooleanValue WEBSOCKET;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
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

    private static ModConfigSpec.BooleanValue define(ModConfigSpec.Builder builder, String key, String comment) {
        return builder.comment(comment).define(key, false);
    }
}
