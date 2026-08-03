package org.arcadia.arc_quest.client.hud.quest.history;

import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.PhaseTransition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.SplashType;
import org.arcadia.arc_quest.quest.api.VisualAsset;
import org.arcadia.arc_quest.client.hud.quest.graph.GraphNodeLayout;
import org.arcadia.arc_quest.client.hud.quest.graph.PhaseGraphLayoutEngine;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
        List<QuestHistoryNodeData> nodes = new ArrayList<>();
        int horizontalSpacing = QuestHistoryNodeRenderer.CARD_WIDTH + 64;
        int verticalSpacing = QuestHistoryNodeRenderer.CARD_HEIGHT + 38;
        List<String> phaseIds = new ArrayList<>(definition.getPhaseIds());
        List<GraphNodeLayout> layouts = PhaseGraphLayoutEngine.layout(phaseIds, phaseId -> {
            PhaseDefinition phase = definition.getPhase(phaseId);
            if (phase == null) return List.of();
            return phase.getTransitions().stream().map(PhaseTransition::getTargetPhaseId).toList();
        }, horizontalSpacing, verticalSpacing);
        for (GraphNodeLayout layout : layouts) {
            String phaseId = layout.id();
            PhaseDefinition phase = definition.getPhase(phaseId);
            if (phase == null) continue;
<<<<<<< HEAD
            int sourceDepth = depths.getOrDefault(phaseId, 0);
            for (PhaseTransition transition : phase.getTransitions()) {
                for (String targetId : transition.getTargetPhaseIds()) {
                    depths.put(targetId, Math.max(depths.getOrDefault(targetId, 0), sourceDepth + 1));
                }
            }
=======
            boolean isCompleted = completed.contains(phaseId);
            boolean isActive = !isCompleted && activePhases.contains(phaseId);
            nodes.add(new QuestHistoryNodeData(
                    phaseId,
                    layout.x(),
                    layout.y(),
                    layout.depth(),
                    isCompleted,
                    isActive,
                    isCompleted || isActive,
                    ClientQuestCache.INSTANCE.getPhaseDisplayComponent(questId, phaseId),
                    phase,
                    resolvePhaseImage(phase)
            ));
>>>>>>> 6ef14700 (提取任务阶段拓扑与视口组件)
        }
        return nodes;
    }

    private static VisualAsset resolvePhaseImage(PhaseDefinition phase) {
        for (SplashType type : IMAGE_PRIORITY) {
            VisualAsset asset = phase.getSplashConfig(type).orElse(null);
            if (asset != null && (asset.texture() != null || asset.item() != null)) return asset;
        }
        return null;
    }
}
