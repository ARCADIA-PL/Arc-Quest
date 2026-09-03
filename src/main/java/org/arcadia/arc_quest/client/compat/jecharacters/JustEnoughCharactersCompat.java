package org.arcadia.arc_quest.client.compat.jecharacters;

import net.neoforged.fml.ModList;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import java.lang.reflect.Method;

/** Optional bridge to Just Enough Characters' PinIn matcher. */
public final class JustEnoughCharactersCompat {
    private static final String MOD_ID = "jecharacters";
    private static final String MATCH_CLASS = "me.towdium.jecharacters.utils.Match";

    private static final Method CONTAINS = findContainsMethod();
    private static boolean disabled;

    private JustEnoughCharactersCompat() {
    }

    public static boolean isAvailable() {
        return CONTAINS != null && !disabled;
    }

    public static boolean matches(String text, String query) {
        if (!isAvailable() || text == null || query == null || query.isBlank()) return false;
        try {
            return Boolean.TRUE.equals(CONTAINS.invoke(null, text, query));
        } catch (ReflectiveOperationException | LinkageError exception) {
            disabled = true;
            ArcQuestLog.debug(ArcQuestLog.Category.COMPAT,
                    "Just Enough Characters matcher is unavailable; using Arc-Quest fallback search", exception);
            return false;
        }
    }

    private static Method findContainsMethod() {
        if (!ModList.get().isLoaded(MOD_ID)) return null;
        try {
            Class<?> matchClass = Class.forName(MATCH_CLASS, false,
                    JustEnoughCharactersCompat.class.getClassLoader());
            return matchClass.getMethod("contains", String.class, CharSequence.class);
        } catch (ReflectiveOperationException | LinkageError exception) {
            ArcQuestLog.debug(ArcQuestLog.Category.COMPAT,
                    "Just Enough Characters detected without a compatible matcher", exception);
            return null;
        }
    }
}
