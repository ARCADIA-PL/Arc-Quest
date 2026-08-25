package org.arcadia.arc_quest.client.hud.quest.journal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 任务日志标签页可见性扩展点。 */
public final class JournalTabVisibilityRegistry {
    private static final List<Filter> FILTERS = new CopyOnWriteArrayList<>();

    private JournalTabVisibilityRegistry() {
    }

    public static void register(Filter filter) {
        if (filter != null) FILTERS.add(filter);
    }

    public static boolean isVisible(JournalTypes.Tab tab) {
        for (Filter filter : FILTERS) {
            if (!filter.isVisible(tab)) return false;
        }
        return true;
    }

    @FunctionalInterface
    public interface Filter {
        boolean isVisible(JournalTypes.Tab tab);
    }
}