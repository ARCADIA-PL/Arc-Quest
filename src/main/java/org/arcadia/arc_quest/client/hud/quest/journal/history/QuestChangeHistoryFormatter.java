package org.arcadia.arc_quest.client.hud.quest.journal.history;

import org.arcadia.arc_quest.client.hud.HudText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
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
        return questNameComponent(questId).getString();
    }

    public static Component questNameComponent(String questId) {
        if (questId == null || questId.isEmpty()) return HudText.of("history.unknown_quest");
        return ClientQuestCache.INSTANCE.getQuestDisplayComponent(questId).copy();
    }

    public static String phaseName(String questId, String phaseId) {
        return phaseNameComponent(questId, phaseId).getString();
    }

    public static Component phaseNameComponent(String questId, String phaseId) {
        if (phaseId == null || phaseId.isEmpty()) return Component.empty();
        if (questId == null || questId.isEmpty()) return Component.literal(phaseId);
        return ClientQuestCache.INSTANCE.getPhaseDisplayComponent(questId, phaseId).copy();
    }

    public static String objectiveName(String questId, String phaseId, int index) {
        return objectiveNameComponent(questId, phaseId, index).getString();
    }

    public static Component objectiveNameComponent(String questId, String phaseId, int index) {
        if (questId == null || phaseId == null || index < 0) return HudText.of("history.objective_fallback", Math.max(0, index + 1));
        ResourceLocation rl = ResourceLocation.tryParse(questId);
        QuestDefinition def = rl != null ? QuestRegistry.get(rl) : null;
        PhaseDefinition phase = def != null ? def.getPhase(phaseId) : null;
        if (phase == null || index >= phase.getObjectives().size()) return HudText.of("history.objective_fallback", index + 1);
        ObjectiveEntry obj = phase.getObjectives().get(index);
        Component text = obj.getDisplayText();
        return text == null || text.getString().isEmpty() ? HudText.of("history.objective_fallback", index + 1) : text.copy();
    }

    public static int objectiveRequired(String questId, String phaseId, int index) {
        if (questId == null || questId.isBlank()) return -1;
        ResourceLocation rl = ResourceLocation.tryParse(questId);
        QuestDefinition def = rl != null ? QuestRegistry.get(rl) : null;
        return objectiveRequired(def, ClientQuestCache.INSTANCE.getActiveQuest(questId), phaseId, index);
    }

    /** Stored history before/after strings retain their own denominator; this resolves current metadata only. */
    static int objectiveRequired(QuestDefinition def, QuestRuntimeData runtime, String phaseId, int index) {
        if (runtime != null && runtime.hasRequiredCount(phaseId, index)) return runtime.getRequiredCount(phaseId, index, 1);
        PhaseDefinition phase = def != null ? def.getPhase(phaseId) : null;
        if (phase == null || index < 0 || index >= phase.getObjectives().size()) return -1;
        int fallback = Math.max(1, phase.getObjectives().get(index).getRequiredCount());
        // An absent historical runtime cannot reconstruct the old player's dynamic requirement.
        return runtime == null ? fallback : runtime.getRequiredCount(phaseId, index, fallback);
    }
}
