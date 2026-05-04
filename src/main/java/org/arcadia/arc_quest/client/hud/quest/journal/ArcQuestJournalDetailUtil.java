package org.arcadia.arc_quest.client.hud.quest.journal;

import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;

public final class ArcQuestJournalDetailUtil {
    private ArcQuestJournalDetailUtil() {
    }

    public static boolean shouldShowBranchChoices(QuestDefinition def, QuestRuntimeData runtime, String phaseId) {
        if (def == null || runtime == null || phaseId == null || phaseId.isEmpty()) return false;
        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase == null || !phase.hasChoices()) return false;
        int[] progress = runtime.getAllProgress(phaseId);
        for (int i = 0; i < phase.getObjectives().size(); i++) {
            if (i >= progress.length || progress[i] < phase.getObjectives().get(i).getRequiredCount()) return false;
        }
        return true;
    }

    public static boolean isPhaseObjectivesDone(QuestRuntimeData runtime, PhaseDefinition phase, String phaseId) {
        int[] progress = runtime.getAllProgress(phaseId);
        for (int i = 0; i < phase.getObjectives().size(); i++) {
            if (i >= progress.length || progress[i] < phase.getObjectives().get(i).getRequiredCount()) return false;
        }
        return true;
    }
}
