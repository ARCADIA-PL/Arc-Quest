package org.arcadia.arc_quest.quest.network;

import org.arcadia.arc_quest.quest.api.ChoiceOption;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.api.HiddenPresentationMode;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

/** Shared, read-only rules for actionable client notices and journal actions. */
public final class QuestNoticePolicy {
    public enum PendingKind { MANUAL_CONFIRM, BRANCH_CHOICE }
    public record PendingPhase(String phaseId, PendingKind kind) {}

    private QuestNoticePolicy() {}

    public static boolean objectivesReady(QuestRuntimeData runtime, PhaseDefinition phase) {
        if (runtime == null || phase == null) return false;
        java.util.Set<String> bound = new java.util.HashSet<>();
        if (phase.hasCollectionSheet()) {
            if (!ClientQuestCache.INSTANCE.getCollectionSheetProgress(runtime.getQuestId(), phase.getPhaseId()).complete()) return false;
            phase.getCollectionSheet().getBindings().forEach(b -> bound.addAll(b.getObjectiveIds()));
        }
        List<ObjectiveEntry> objectives = phase.getObjectives();
        for (int index = 0; index < objectives.size(); index++) {
            ObjectiveEntry objective = objectives.get(index);
            // Match server progression: NULL is informational; hidden and optional
            // still participate in its actual completion check.
            if (objective.getType().equals(ObjectiveType.NULL) || (phase.hasCollectionSheet()
                    && (objective.isOptional() || bound.contains(objective.getObjectiveId())))) continue;
            int required = runtime.getRequiredCount(phase.getPhaseId(), index, objective.getRequiredCount());
            if (runtime.getObjectiveProgress(phase.getPhaseId(), index) < required) return false;
        }
        return true;
    }

    public static boolean branchReady(QuestDefinition definition, QuestRuntimeData runtime, String phaseId) {
        if (definition == null || runtime == null || runtime.getState() != QuestState.ACTIVE
                || phaseId == null || !runtime.isPhaseActive(phaseId)) return false;
        PhaseDefinition phase = definition.getPhase(phaseId);
        return phase != null && phase.hasChoices() && objectivesReady(runtime, phase);
    }

    public static List<PendingPhase> pendingPhases(QuestDefinition definition, QuestRuntimeData runtime,
                                                 Predicate<ChoiceOption> visibleChoice) {
        if (definition == null || runtime == null || runtime.getState() != QuestState.ACTIVE
                || (definition.isCollectionQuest() && !definition.hasCollectionSheets())) return List.of();
        List<PendingPhase> result = new ArrayList<>();
        for (String phaseId : definition.getPhaseIds()) {
            if (!runtime.isPhaseActive(phaseId)) continue;
            PhaseDefinition phase = definition.getPhase(phaseId);
            if (phase == null) continue;
            if (phase.hasChoices()) {
                if (objectivesReady(runtime, phase)
                        && phase.getChoices().stream().anyMatch(visibleChoice)) {
                    result.add(new PendingPhase(phaseId, PendingKind.BRANCH_CHOICE));
                }
            } else if (runtime.isPhasePendingManualAdvance(phaseId)) {
                result.add(new PendingPhase(phaseId, PendingKind.MANUAL_CONFIRM));
            }
        }
        return List.copyOf(result);
    }

    public static boolean visibleObjective(ObjectiveEntry objective) {
        return objective != null && !objective.isHidden() && !objective.getType().equals(ObjectiveType.NULL);
    }

    public static boolean completedNow(int oldProgress, int newProgress, int oldRequired, int required) {
        return required > 0 && newProgress >= required && (oldRequired <= 0 || oldProgress < oldRequired);
    }

    public static boolean transientUpdatesAllowed(QuestRuntimeData previous, QuestRuntimeData current) {
        return previous != null && current != null && previous.getState() == QuestState.ACTIVE
                && current.getState() == QuestState.ACTIVE;
    }

    public static Set<String> newlyCompletedPhases(QuestRuntimeData previous, QuestRuntimeData current) {
        if (previous == null || current == null) return Set.of();
        Set<String> completed = new LinkedHashSet<>(current.getCompletedPhaseIds());
        completed.removeAll(previous.getCompletedPhaseIds());
        return completed;
    }

    public static Set<String> newlyPendingConfirmations(QuestRuntimeData previous, QuestRuntimeData current) {
        if (current == null || current.getState() != QuestState.ACTIVE) return Set.of();
        Set<String> entered = new LinkedHashSet<>(current.getPendingManualAdvancePhaseIds());
        entered.retainAll(current.getActivePhaseIds());
        if (previous != null && previous.getState() == QuestState.ACTIVE)
            entered.removeAll(previous.getPendingManualAdvancePhaseIds());
        return entered;
    }

    public static boolean visibleCollectionEntry(QuestDefinition definition, QuestRuntimeData runtime, String phaseId) {
        if (definition == null || runtime == null) return false;
        PhaseDefinition phase = definition.getPhase(phaseId);
        if (phase == null || !phase.hasCollectionEntryConfig()) return false;
        var collection = runtime.getCollectionData();
        if (collection == null || (!collection.isVisible(phaseId) && !collection.isDiscovered(phaseId))) return false;
        // A placeholder/silhouette/name mask must not reveal the entry's real name.
        return collection.isDiscovered(phaseId)
                || phase.getCollectionEntryConfig().getHiddenPresentationMode() == HiddenPresentationMode.FULLY_HIDDEN;
    }
}
