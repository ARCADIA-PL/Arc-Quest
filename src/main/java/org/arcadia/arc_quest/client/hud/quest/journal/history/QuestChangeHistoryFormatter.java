package org.arcadia.arc_quest.client.hud.quest.journal.history;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class QuestChangeHistoryFormatter {
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss", Locale.ROOT);
    private static final SimpleDateFormat FULL_TIME_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.ROOT);

    private QuestChangeHistoryFormatter() {
    }

    public static String time(long timeMs) {
        return TIME_FORMAT.format(new Date(timeMs));
    }

    public static String fullTime(long timeMs) {
        return FULL_TIME_FORMAT.format(new Date(timeMs));
    }

    public static String questName(String questId) {
        if (questId == null || questId.isEmpty()) return "Unknown Quest";
        return ClientQuestCache.INSTANCE.getQuestDisplayName(questId);
    }

    public static String phaseName(String questId, String phaseId) {
        if (phaseId == null || phaseId.isEmpty()) return "";
        return ClientQuestCache.INSTANCE.getPhaseDisplayName(questId, phaseId);
    }

    public static String objectiveName(String questId, String phaseId, int index) {
        if (questId == null || phaseId == null || index < 0) return "Objective " + Math.max(0, index + 1);
        ResourceLocation rl = ResourceLocation.tryParse(questId);
        QuestDefinition def = rl != null ? QuestRegistry.get(rl) : null;
        PhaseDefinition phase = def != null ? def.getPhase(phaseId) : null;
        if (phase == null || index >= phase.getObjectives().size()) return "Objective " + (index + 1);
        ObjectiveEntry obj = phase.getObjectives().get(index);
        String text = obj.getDisplayText().getString();
        return text == null || text.isEmpty() ? "Objective " + (index + 1) : text;
    }

    public static int objectiveRequired(String questId, String phaseId, int index) {
        ResourceLocation rl = ResourceLocation.tryParse(questId);
        QuestDefinition def = rl != null ? QuestRegistry.get(rl) : null;
        PhaseDefinition phase = def != null ? def.getPhase(phaseId) : null;
        if (phase == null || index < 0 || index >= phase.getObjectives().size()) return -1;
        return Math.max(1, phase.getObjectives().get(index).getRequiredCount());
    }
}
