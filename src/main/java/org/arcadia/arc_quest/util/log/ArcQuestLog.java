package org.arcadia.arc_quest.util.log;

import com.mojang.logging.LogUtils;
import org.arcadia.arc_quest.config.ArcQuestLogConfig;
import org.slf4j.Logger;

import java.util.EnumSet;
import java.util.Locale;

/**
 * Arc Quest 的分类日志门面。诊断分类默认关闭，ERROR 始终交由日志后端记录。
 */
public final class ArcQuestLog {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final EnumSet<Category> ENABLED = EnumSet.noneOf(Category.class);

    private ArcQuestLog() {
    }

    public enum Category {
        CORE,
        QUEST,
        QUEST_PROGRESS,
        QUEST_NETWORK,
        QUEST_RELOAD,
        DIALOGUE,
        DIALOGUE_NETWORK,
        GUIDE,
        TRADE,
        GACHA,
        NPC,
        MARKER,
        HUD,
        RENDER,
        COMMAND,
        COMPAT,
        DATA,
        API,
        PERSISTENCE,
        WEBSOCKET;

        public String configKey() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static Logger rawLogger() {
        return LOGGER;
    }

    public static synchronized void loadFromConfig() {
        for (Category category : Category.values()) {
            setEnabled(category, readConfig(category));
        }
    }

    public static synchronized void enable(Category category) {
        setEnabled(category, true);
        writeConfig(category, true);
    }

    public static synchronized void disable(Category category) {
        setEnabled(category, false);
        writeConfig(category, false);
    }

    public static synchronized void toggle(Category category) {
        if (ENABLED.contains(category)) {
            disable(category);
        } else {
            enable(category);
        }
    }

    public static synchronized boolean isEnabled(Category category) {
        return ENABLED.contains(category);
    }

    public static synchronized String listStatus() {
        StringBuilder result = new StringBuilder("Arc Quest log categories:\n");
        for (Category category : Category.values()) {
            result.append("  ")
                    .append(String.format(Locale.ROOT, "%-16s", category.name()))
                    .append(ENABLED.contains(category) ? "[ON]" : "[OFF]")
                    .append('\n');
        }
        return result.toString();
    }

    public static void debug(Category category, String message, Object... arguments) {
        if (isEnabled(category)) {
            LOGGER.debug(tag(category) + " " + message, arguments);
        }
    }

    public static void info(Category category, String message, Object... arguments) {
        if (isEnabled(category)) {
            LOGGER.info(tag(category) + " " + message, arguments);
        }
    }

    public static void warn(Category category, String message, Object... arguments) {
        if (isEnabled(category)) {
            LOGGER.warn(tag(category) + " " + message, arguments);
        }
    }

    public static void error(Category category, String message, Object... arguments) {
        LOGGER.error(tag(category) + " " + message, arguments);
    }

    private static String tag(Category category) {
        return "[" + category.name() + "]";
    }

    private static void setEnabled(Category category, boolean enabled) {
        if (enabled) {
            ENABLED.add(category);
        } else {
            ENABLED.remove(category);
        }
    }

    private static boolean readConfig(Category category) {
        return switch (category) {
            case CORE -> ArcQuestLogConfig.CORE.get();
            case QUEST -> ArcQuestLogConfig.QUEST.get();
            case QUEST_PROGRESS -> ArcQuestLogConfig.QUEST_PROGRESS.get();
            case QUEST_NETWORK -> ArcQuestLogConfig.QUEST_NETWORK.get();
            case QUEST_RELOAD -> ArcQuestLogConfig.QUEST_RELOAD.get();
            case DIALOGUE -> ArcQuestLogConfig.DIALOGUE.get();
            case DIALOGUE_NETWORK -> ArcQuestLogConfig.DIALOGUE_NETWORK.get();
            case GUIDE -> ArcQuestLogConfig.GUIDE.get();
            case TRADE -> ArcQuestLogConfig.TRADE.get();
            case GACHA -> ArcQuestLogConfig.GACHA.get();
            case NPC -> ArcQuestLogConfig.NPC.get();
            case MARKER -> ArcQuestLogConfig.MARKER.get();
            case HUD -> ArcQuestLogConfig.HUD.get();
            case RENDER -> ArcQuestLogConfig.RENDER.get();
            case COMMAND -> ArcQuestLogConfig.COMMAND.get();
            case COMPAT -> ArcQuestLogConfig.COMPAT.get();
            case DATA -> ArcQuestLogConfig.DATA.get();
            case API -> ArcQuestLogConfig.API.get();
            case PERSISTENCE -> ArcQuestLogConfig.PERSISTENCE.get();
            case WEBSOCKET -> ArcQuestLogConfig.WEBSOCKET.get();
        };
    }

    private static void writeConfig(Category category, boolean enabled) {
        switch (category) {
            case CORE -> ArcQuestLogConfig.CORE.set(enabled);
            case QUEST -> ArcQuestLogConfig.QUEST.set(enabled);
            case QUEST_PROGRESS -> ArcQuestLogConfig.QUEST_PROGRESS.set(enabled);
            case QUEST_NETWORK -> ArcQuestLogConfig.QUEST_NETWORK.set(enabled);
            case QUEST_RELOAD -> ArcQuestLogConfig.QUEST_RELOAD.set(enabled);
            case DIALOGUE -> ArcQuestLogConfig.DIALOGUE.set(enabled);
            case DIALOGUE_NETWORK -> ArcQuestLogConfig.DIALOGUE_NETWORK.set(enabled);
            case GUIDE -> ArcQuestLogConfig.GUIDE.set(enabled);
            case TRADE -> ArcQuestLogConfig.TRADE.set(enabled);
            case GACHA -> ArcQuestLogConfig.GACHA.set(enabled);
            case NPC -> ArcQuestLogConfig.NPC.set(enabled);
            case MARKER -> ArcQuestLogConfig.MARKER.set(enabled);
            case HUD -> ArcQuestLogConfig.HUD.set(enabled);
            case RENDER -> ArcQuestLogConfig.RENDER.set(enabled);
            case COMMAND -> ArcQuestLogConfig.COMMAND.set(enabled);
            case COMPAT -> ArcQuestLogConfig.COMPAT.set(enabled);
            case DATA -> ArcQuestLogConfig.DATA.set(enabled);
            case API -> ArcQuestLogConfig.API.set(enabled);
            case PERSISTENCE -> ArcQuestLogConfig.PERSISTENCE.set(enabled);
            case WEBSOCKET -> ArcQuestLogConfig.WEBSOCKET.set(enabled);
        }
    }
}
