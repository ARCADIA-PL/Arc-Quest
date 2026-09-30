package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconContext;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveRowLayout;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;

/** Same-package access exercises the exact row layout called by both production phase views. */
public final class ObjectiveIconRowProbe {
    private ObjectiveIconRowProbe() {}
    public static ObjectiveRowLayout layout(QuestJournalScreen screen, ObjectiveIconContext context, boolean compact) {
        return ObjectiveRowRenderer.layout(screen, context, compact ? 180 : 320, compact);
    }
}
