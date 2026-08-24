package org.arcadia.arc_quest.client.hud.quest.trackingmenu;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.SplashType;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.service.QuestTrackingPriority;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

record QuestTrackingMenuEntry(String questId,
                              QuestDefinition definition,
                              QuestRuntimeData runtime,
                              List<QuestTrackingMenuPhaseEntry> activePhases,
                              @Nullable ResourceLocation splashTexture) {

    static List<QuestTrackingMenuEntry> snapshot() {
        List<QuestTrackingMenuEntry> entries = new ArrayList<>();
        for (Map.Entry<String, QuestRuntimeData> activeEntry
                : ClientQuestCache.INSTANCE.getAllActiveQuests().entrySet()) {
            String questId = activeEntry.getKey();
            ResourceLocation questKey = ResourceLocation.tryParse(questId);
            QuestDefinition definition = questKey == null ? null : QuestRegistry.get(questKey);
            if (definition == null) continue;

            QuestRuntimeData runtime = activeEntry.getValue();
            List<QuestTrackingMenuPhaseEntry> activePhases = orderedActivePhases(definition, runtime);
            ResourceLocation splashTexture = definition.getSplashConfig(SplashType.QUEST_ACQUIRED)
                    .map(asset -> asset.texture())
                    .orElse(null);
            entries.add(new QuestTrackingMenuEntry(
                    questId, definition, runtime, activePhases, splashTexture));
        }

        entries.sort(Comparator.comparing(entry -> QuestTrackingPriority.resolve(
                entry.questId(), entry.runtime().getAcceptedAtTick())));
        return List.copyOf(entries);
    }

    static List<QuestTrackingMenuPhaseEntry> orderedActivePhases(
            QuestDefinition definition, QuestRuntimeData runtime) {
        List<QuestTrackingMenuPhaseEntry> phases = new ArrayList<>();
        for (String phaseId : definition.getPhaseIds()) {
            if (!runtime.isPhaseActive(phaseId)) continue;
            PhaseDefinition phase = definition.getPhase(phaseId);
            if (phase != null) phases.add(new QuestTrackingMenuPhaseEntry(phaseId, phase));
        }
        return List.copyOf(phases);
    }

    @Nullable
    QuestTrackingMenuPhaseEntry phaseById(@Nullable String phaseId) {
        if (phaseId == null) return null;
        for (QuestTrackingMenuPhaseEntry phase : activePhases) {
            if (phase.phaseId().equals(phaseId)) return phase;
        }
        return null;
    }

    @Nullable
    QuestTrackingMenuPhaseEntry firstActivePhase() {
        return activePhases.isEmpty() ? null : activePhases.getFirst();
    }
}
