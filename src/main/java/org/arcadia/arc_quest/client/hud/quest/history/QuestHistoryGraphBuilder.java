package org.arcadia.arc_quest.client.hud.quest.history;

import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.PhaseTransition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.SplashType;
import org.arcadia.arc_quest.quest.api.VisualAsset;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class QuestHistoryGraphBuilder {

    private static final List<SplashType> IMAGE_PRIORITY = List.of(
            SplashType.QUEST_DETAIL,
            SplashType.PHASE_START,
            SplashType.PHASE_COMPLETE,
            SplashType.QUEST_ACQUIRED,
            SplashType.QUEST_COMPLETED,
            SplashType.DIALOGUE_START,
            SplashType.DIALOGUE_END,
            SplashType.QUEST_FAILED
    );

    private QuestHistoryGraphBuilder() {
    }

    static List<QuestHistoryNodeData> build(String questId, QuestDefinition definition, QuestRuntimeData runtime) {
        Set<String> completed = runtime == null ? definition.getPhaseIds() : runtime.getCompletedPhaseIds();
        Set<String> activePhases = runtime == null ? Collections.emptySet() : runtime.getActivePhaseIds();
        Map<String, Integer> depths = calculateDepths(definition);
        Map<Integer, List<String>> byDepth = new HashMap<>();
        for (String phaseId : definition.getPhaseIds()) {
            byDepth.computeIfAbsent(depths.getOrDefault(phaseId, 0), ignored -> new ArrayList<>()).add(phaseId);
        }

        List<QuestHistoryNodeData> nodes = new ArrayList<>();
        int horizontalSpacing = QuestHistoryNodeRenderer.CARD_WIDTH + 64;
        int verticalSpacing = QuestHistoryNodeRenderer.CARD_HEIGHT + 38;
        for (Map.Entry<Integer, List<String>> layer : byDepth.entrySet()) {
            int total = layer.getValue().size();
            for (int index = 0; index < total; index++) {
                String phaseId = layer.getValue().get(index);
                PhaseDefinition phase = definition.getPhase(phaseId);
                if (phase == null) continue;
                boolean isCompleted = completed.contains(phaseId);
                boolean isActive = !isCompleted && activePhases.contains(phaseId);
                nodes.add(new QuestHistoryNodeData(
                        phaseId,
                        layer.getKey() * horizontalSpacing,
                        Math.round((index - (total - 1) / 2f) * verticalSpacing),
                        layer.getKey(),
                        isCompleted,
                        isActive,
                        isCompleted || isActive,
                        ClientQuestCache.INSTANCE.getPhaseDisplayComponent(questId, phaseId),
                        phase,
                        resolvePhaseImage(phase)
                ));
            }
        }
        return nodes;
    }

    private static Map<String, Integer> calculateDepths(QuestDefinition definition) {
        Map<String, Integer> depths = new HashMap<>();
        for (String phaseId : definition.getPhaseIds()) depths.put(phaseId, 0);
        for (String phaseId : definition.getPhaseIds()) {
            PhaseDefinition phase = definition.getPhase(phaseId);
            if (phase == null) continue;
            int sourceDepth = depths.getOrDefault(phaseId, 0);
            for (PhaseTransition transition : phase.getTransitions()) {
                for (String targetId : transition.getTargetPhaseIds()) {
                    depths.put(targetId, Math.max(depths.getOrDefault(targetId, 0), sourceDepth + 1));
                }
            }
        }
        return depths;
    }

    private static VisualAsset resolvePhaseImage(PhaseDefinition phase) {
        for (SplashType type : IMAGE_PRIORITY) {
            VisualAsset asset = phase.getSplashConfig(type).orElse(null);
            if (asset != null && (asset.texture() != null || asset.item() != null)) return asset;
        }
        return null;
    }
}
