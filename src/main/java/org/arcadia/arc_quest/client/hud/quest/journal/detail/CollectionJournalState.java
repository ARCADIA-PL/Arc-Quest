package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Browser state is deliberately independent from quest tracking and survives a JEI return. */
public final class CollectionJournalState {
    private static final Map<String, CollectionJournalState> MEMORY = new LinkedHashMap<>(32, .75f, true);
    private static final Map<String, String> PHASES = new LinkedHashMap<>(32, .75f, true);
    private static Object connection;
    public String category = "";
    public String query = "";
    public String selection = "";
    public boolean expanded;
    public boolean selectionMade;
    public int categoryOffset;
    public double catalogScroll;
    public double detailScroll;

    public static synchronized void clear() {
        MEMORY.clear();
        PHASES.clear();
        connection = null;
    }

    /** A new journal session clears searches, while resize and JEI suspension keep this memory. */
    public static synchronized void resetBrowserOnOpen(Object currentConnection) {
        if (connection != currentConnection) { clear(); connection = currentConnection; }
        for (CollectionJournalState browser : MEMORY.values()) {
            browser.query = "";
            browser.catalogScroll = 0;
            browser.expanded = false;
            browser.selectionMade = false;
        }
    }

    public static synchronized CollectionJournalState get(Object currentConnection, String quest,
                                                          long run, String phase) {
        if (connection != currentConnection) {
            MEMORY.clear();
            PHASES.clear();
            connection = currentConnection;
        }
        String key = quest + "/" + run + "/" + phase;
        CollectionJournalState state = MEMORY.computeIfAbsent(key, ignored -> new CollectionJournalState());
        while (MEMORY.size() > 128) MEMORY.remove(MEMORY.keySet().iterator().next());
        return state;
    }

    public static synchronized String phase(Object currentConnection, String quest, long run, String fallback) {
        get(currentConnection, quest, run, fallback);
        return PHASES.getOrDefault(quest + "/" + run, fallback);
    }

    public static synchronized void rememberPhase(String quest, long run, String phase) {
        PHASES.put(quest + "/" + run, phase);
        while (PHASES.size() > 128) PHASES.remove(PHASES.keySet().iterator().next());
    }

    public void select(String bindingId) {
        if (!Objects.equals(selection, bindingId)) detailScroll = 0;
        selection = bindingId;
        expanded = true;
        selectionMade = true;
    }

    public void validate(List<String> availableIds) {
        if (!availableIds.contains(selection)) {
            selection = availableIds.isEmpty() ? "" : availableIds.get(0);
            detailScroll = 0;
            expanded = false;
            selectionMade = false;
        }
    }

    public static double clampScroll(double value, int content, int viewport) {
        return Math.max(0, Math.min(value, Math.max(0, content - viewport)));
    }
}
