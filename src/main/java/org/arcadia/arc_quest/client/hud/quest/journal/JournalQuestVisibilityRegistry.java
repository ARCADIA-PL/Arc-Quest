package org.arcadia.arc_quest.client.hud.quest.journal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/** Presentation-only filters; hidden quests retain their server state and collection knowledge. */
public final class JournalQuestVisibilityRegistry {
    private static final List<Predicate<String>> FILTERS = new CopyOnWriteArrayList<>();

    private JournalQuestVisibilityRegistry() {}

    public static void register(Predicate<String> filter) {
        if (filter != null) FILTERS.add(filter);
    }

    public static boolean isVisible(String questId) {
        return FILTERS.stream().allMatch(filter -> filter.test(questId));
    }
}
