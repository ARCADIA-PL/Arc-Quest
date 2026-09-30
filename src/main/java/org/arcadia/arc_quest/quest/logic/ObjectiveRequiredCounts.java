package org.arcadia.arc_quest.quest.logic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import java.util.List;

/** Projects effective server thresholds onto the existing quest state stream, independently of JEI. */
public final class ObjectiveRequiredCounts {
    private ObjectiveRequiredCounts() {}

    public static boolean refresh(ServerPlayer player, ArcQuestPlayer data, QuestRuntimeData runtime) {
        if (player == null || data == null || runtime == null || runtime.getState() != QuestState.ACTIVE) return false;
        ResourceLocation id = ResourceLocation.tryParse(runtime.getQuestId());
        var definition = id == null ? null : QuestRegistry.get(id);
        if (definition == null) return false;
        boolean changed = false;
        for (String phaseId : runtime.getActivePhaseIds()) {
            var phase = definition.getPhase(phaseId);
            if (phase == null) continue;
            int[] required = new int[runtime.getObjectiveCount(phaseId)];
            for (int i = 0; i < required.length && i < phase.getObjectives().size(); i++) {
                required[i] = QuestProgressHandler.resolveRequiredCount(player, phase.getObjectives().get(i), data);
            }
            changed |= runtime.setRequiredCounts(phaseId, required);
        }
        return changed;
    }

    public static void refreshAll(ServerPlayer player, ArcQuestPlayer data) {
        if (data == null) return;
        for (var runtime : List.copyOf(data.getAllActiveQuests().values())) refresh(player, data, runtime);
    }
}
