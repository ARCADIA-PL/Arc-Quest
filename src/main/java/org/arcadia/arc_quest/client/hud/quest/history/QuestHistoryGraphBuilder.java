package org.arcadia.arc_quest.client.hud.quest.history;

import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.SplashType;
import org.arcadia.arc_quest.quest.api.VisualAsset;
import org.arcadia.arc_quest.quest.api.CollectionEntryDefinition;
import org.arcadia.arc_quest.quest.api.HiddenPresentationMode;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.data.CollectionBindingProgress;
import org.arcadia.arc_quest.quest.data.CollectionSheetProgress;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.quest.graph.GraphNodeLayout;
import org.arcadia.arc_quest.client.hud.quest.graph.PhaseGraphLayoutEngine;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.function.Function;

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
        if (definition.hasCollectionSheets()) return buildCollection(definition, runtime,
                id -> ClientQuestCache.INSTANCE.getPhaseDisplayComponent(questId, id),
                id -> ClientQuestCache.INSTANCE.getCollectionSheetProgress(questId, id));
        Set<String> completed = runtime == null ? definition.getPhaseIds() : runtime.getCompletedPhaseIds();
        Set<String> activePhases = runtime == null ? Collections.emptySet() : runtime.getActivePhaseIds();
        List<QuestHistoryNodeData> nodes = new ArrayList<>();
        int horizontalSpacing = QuestHistoryNodeRenderer.CARD_WIDTH + 64;
        int verticalSpacing = QuestHistoryNodeRenderer.CARD_HEIGHT + 38;
        List<String> phaseIds = new ArrayList<>(definition.getPhaseIds());
        List<GraphNodeLayout> layouts = PhaseGraphLayoutEngine.layout(phaseIds, phaseId -> {
            PhaseDefinition phase = definition.getPhase(phaseId);
            if (phase == null) return List.of();
            return targets(phase);
        }, horizontalSpacing, verticalSpacing);
        for (GraphNodeLayout layout : layouts) {
            String phaseId = layout.id();
            PhaseDefinition phase = definition.getPhase(phaseId);
            if (phase == null) continue;
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
        }
        return nodes;
    }

    /** True phase topology with binding children; a binding never becomes a fabricated PhaseDefinition. */
    static List<QuestHistoryNodeData> buildCollection(QuestDefinition definition, QuestRuntimeData runtime,
            Function<String, Component> names, Function<String, CollectionSheetProgress> sheets) {
        List<String> phaseIds = new ArrayList<>(definition.getPhaseIds());
        Map<String, CollectionSheetProgress> progress = new LinkedHashMap<>();
        Map<Integer, List<GraphNodeLayout>> layers = new LinkedHashMap<>();
        for (GraphNodeLayout position : PhaseGraphLayoutEngine.layout(phaseIds,
                id -> targets(definition.getPhase(id)), 250, 1)) {
            boolean reached = runtime != null && (runtime.isPhaseActive(position.id()) || runtime.isPhaseCompleted(position.id()));
            progress.put(position.id(), displaySheet(definition, definition.getPhase(position.id()),
                    reached ? sheets.apply(position.id()) : null));
            layers.computeIfAbsent(position.depth(), ignored -> new ArrayList<>()).add(position);
        }
        List<QuestHistoryNodeData> nodes = new ArrayList<>();
        int row = QuestHistoryNodeRenderer.CARD_HEIGHT + 26;
        for (var layer : layers.values()) {
            Map<String, Integer> heights = new LinkedHashMap<>();
            int total = 0;
            for (GraphNodeLayout position : layer) {
                CollectionSheetProgress sheet = progress.get(position.id());
                int children = sheet == null ? 0 : (int) sheet.bindings().stream().filter(binding -> binding.visible()).count();
                int height = Math.max(row, (children + 1) * row) + 35;
                heights.put(position.id(), height); total += height;
            }
            int top = -total / 2;
            for (GraphNodeLayout position : layer) {
                PhaseDefinition phase = definition.getPhase(position.id());
                CollectionSheetProgress sheet = phase.hasCollectionSheet() ? progress.get(position.id()) : null;
                boolean completed = runtime != null && runtime.isPhaseCompleted(phase.getPhaseId());
                boolean active = runtime != null && runtime.getState() == QuestState.ACTIVE
                        && !completed && runtime.isPhaseActive(phase.getPhaseId());
                // Definition membership alone does not prove a reached or completed run.
                boolean reached = completed || runtime != null && runtime.isPhaseActive(phase.getPhaseId());
                int y = top + QuestHistoryNodeRenderer.CARD_HEIGHT / 2;
                nodes.add(new QuestHistoryNodeData(phase.getPhaseId(), position.x(), y, position.depth(),
                        completed, active, reached, names.apply(phase.getPhaseId()), phase,
                        reached ? resolvePhaseImage(phase) : null, sheet, null, null, false));
                if (sheet != null) {
                    int index = 1;
                    for (var binding : sheet.bindings()) {
                        if (!binding.visible()) continue;
                        CollectionEntryDefinition entry = binding.revealed() ? definition.getCollectionConfig().getEntry(binding.entryId()) : null;
                        var bindingDefinition = phase.getCollectionSheet().getBinding(binding.bindingId());
                        Component title = entry == null ? Component.translatable("arc_quest.hud.history.collection_hidden") : entry.getDisplayName();
                        nodes.add(new QuestHistoryNodeData(bindingNodeId(phase.getPhaseId(), binding.bindingId()),
                                position.x() + 28, y + index++ * row, position.depth(), binding.complete(),
                                active && !binding.complete(), reached && binding.revealed(), title, phase, null,
                                null, binding, entry, bindingDefinition != null && bindingDefinition.isOptional()));
                    }
                }
                top += heights.get(position.id());
            }
        }
        return List.copyOf(nodes);
    }

    private static CollectionSheetProgress displaySheet(QuestDefinition quest, PhaseDefinition phase, CollectionSheetProgress progress) {
        if (!phase.hasCollectionSheet()) return null;
        if (progress != null) return progress;
        // Later phases have no frozen run yet. Keep anonymous structure, without inventing action progress.
        List<CollectionBindingProgress> bindings = new ArrayList<>();
        for (var binding : phase.getCollectionSheet().getBindings()) {
            CollectionEntryDefinition entry = quest.getCollectionConfig().getEntry(binding.getEntryId());
            if (entry == null) continue;
            boolean visible = entry.getVisibilityMode() == org.arcadia.arc_quest.quest.api.VisibilityMode.VISIBLE_BY_DEFAULT
                    || entry.getHiddenPresentationMode() != HiddenPresentationMode.FULLY_HIDDEN;
            bindings.add(new CollectionBindingProgress(binding.getBindingId(), binding.getEntryId(), visible,
                    false, false, false, false, List.of(), List.of()));
        }
        return new CollectionSheetProgress(0, phase.getCollectionSheet().getRequiredCount(),
                (int) phase.getCollectionSheet().getBindings().stream().filter(binding -> !binding.isOptional())
                        .map(binding -> phase.getCollectionSheet().isCountDistinctEntries() ? binding.getEntryId().toString() : binding.getBindingId()).distinct().count(),
                false, bindings, List.of());
    }

    static String bindingNodeId(String phaseId, String bindingId) {
        // View-only namespacing prevents a child's stable binding ID colliding with a real phase ID.
        return "\u0000binding/" + phaseId + "\u0000" + bindingId;
    }

    static List<String> targets(PhaseDefinition phase) {
        if (phase == null) return List.of();
        Set<String> targets = new LinkedHashSet<>();
        phase.getTransitions().forEach(transition -> targets.addAll(transition.getTargetPhaseIds()));
        phase.getChoices().forEach(choice -> targets.add(choice.getTargetPhaseId()));
        return List.copyOf(targets);
    }

    static List<QuestHistoryConnection> connections(List<QuestHistoryNodeData> nodes) {
        Set<String> ids = nodes.stream().map(QuestHistoryNodeData::id).collect(java.util.stream.Collectors.toSet());
        List<QuestHistoryConnection> result = new ArrayList<>();
        for (var node : nodes) {
            if (node.isBinding()) {
                if (ids.contains(node.phaseId())) result.add(new QuestHistoryConnection(node.phaseId(), node.id(), true));
            } else for (String target : targets(node.phase())) {
                if (ids.contains(target)) result.add(new QuestHistoryConnection(node.id(), target, false));
            }
        }
        return List.copyOf(result);
    }

    private static VisualAsset resolvePhaseImage(PhaseDefinition phase) {
        for (SplashType type : IMAGE_PRIORITY) {
            VisualAsset asset = phase.getSplashConfig(type).orElse(null);
            if (asset != null && (asset.texture() != null || asset.item() != null)) return asset;
        }
        return null;
    }
}
