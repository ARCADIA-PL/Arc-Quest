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
                              @Nullable String phaseId,
                              @Nullable PhaseDefinition phase,
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
            String phaseId = resolvePhaseId(definition, runtime);
            PhaseDefinition phase = phaseId == null ? null : definition.getPhase(phaseId);
            ResourceLocation splashTexture = definition.getSplashConfig(SplashType.QUEST_ACQUIRED)
                    .map(asset -> asset.texture())
                    .orElse(null);
            entries.add(new QuestTrackingMenuEntry(
                    questId, definition, runtime, phaseId, phase, splashTexture));
        }

        entries.sort(Comparator.comparing(entry -> QuestTrackingPriority.resolve(
                entry.questId(), entry.runtime().getAcceptedAtTick())));
        return List.copyOf(entries);
    }

    private static String resolvePhaseId(QuestDefinition definition, QuestRuntimeData runtime) {
        String currentPhaseId = runtime.getCurrentPhaseId();
        if (isUsablePhase(definition, runtime, currentPhaseId)) return currentPhaseId;
        for (String activePhaseId : runtime.getActivePhaseIds()) {
            if (isUsablePhase(definition, runtime, activePhaseId)) return activePhaseId;
        }
        return null;
    }

    private static boolean isUsablePhase(QuestDefinition definition, QuestRuntimeData runtime,
                                         @Nullable String phaseId) {
        return phaseId != null && !phaseId.isEmpty()
                && runtime.isPhaseActive(phaseId)
                && definition.getPhase(phaseId) != null;
    }
}
