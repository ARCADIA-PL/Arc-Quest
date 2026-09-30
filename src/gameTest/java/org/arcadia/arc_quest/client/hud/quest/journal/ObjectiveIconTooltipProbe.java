package org.arcadia.arc_quest.client.hud.quest.journal;

import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalTooltipRequest;

import java.lang.reflect.Field;

/** Test-only read of the actual top-level custom tooltip request; no production accessors added. */
public final class ObjectiveIconTooltipProbe {
    private static final Field REQUEST = requestField();
    private ObjectiveIconTooltipProbe() {}
    private static Field requestField() {
        try {
            Field field = QuestJournalScreen.class.getDeclaredField("hoveredObjectiveTooltip");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException error) { throw new ExceptionInInitializerError(error); }
    }
    public static JournalTooltipRequest request(QuestJournalScreen screen) {
        try { return (JournalTooltipRequest) REQUEST.get(screen); }
        catch (IllegalAccessException error) { throw new IllegalStateException(error); }
    }
}
