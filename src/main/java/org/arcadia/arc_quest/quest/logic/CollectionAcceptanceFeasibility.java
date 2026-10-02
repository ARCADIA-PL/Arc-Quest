package org.arcadia.arc_quest.quest.logic;

import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.data.CollectionRecordState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure upper-bound check: accepting a new-discovery task must leave enough eligible candidates. */
public final class CollectionAcceptanceFeasibility {
    private CollectionAcceptanceFeasibility() {}

    public record SheetFeasibility(String phaseId, int possibleCount, int requiredCount) {
        public boolean feasible() { return possibleCount >= requiredCount; }
    }

    public static SheetFeasibility project(QuestDefinition quest, PhaseDefinition phase, CollectionRecordState records) {
        CollectionSheetDefinition sheet = phase.getCollectionSheet();
        if (sheet == null) return new SheetFeasibility(phase.getPhaseId(), 0, 0);
        Map<String, Boolean> candidates = new LinkedHashMap<>();
        for (EntryRequirementBinding binding : sheet.getBindings()) {
            if (binding.isOptional()) continue;
            String key = sheet.isCountDistinctEntries() ? binding.getEntryId().toString() : binding.getBindingId();
            candidates.merge(key, bindingPossible(quest, phase, binding, records), (a, b) -> a && b);
        }
        return new SheetFeasibility(phase.getPhaseId(),
                (int) candidates.values().stream().filter(Boolean::booleanValue).count(), sheet.getRequiredCount());
    }

    /** Conditions and choice branches may become available later; this check never predicts those predicates. */
    public static boolean canAccept(QuestDefinition quest, PhaseDefinition initialPhase, CollectionRecordState records) {
        if (!quest.hasCollectionSheets()) return true;
        if (initialPhase == null || !project(quest, initialPhase, records).feasible()) return false;
        return switch (quest.getCompletionPolicy()) {
            case ALL -> quest.getPhaseIds().stream().map(quest::getPhase).allMatch(phase -> project(quest, phase, records).feasible());
            case SPECIFIC_PHASE -> {
                PhaseDefinition target = quest.getPhase(quest.getCompletionTargetPhaseId());
                yield target != null && project(quest, target, records).feasible();
            }
            case ANY, N_OF_M -> {
                int required = quest.getCompletionPolicy() == QuestCompletionPolicy.ANY
                        ? 1 : Math.max(1, quest.getCompletionRequiredCount());
                long possible = quest.getPhaseIds().stream().map(quest::getPhase)
                        .filter(phase -> project(quest, phase, records).feasible()).count();
                yield possible >= required;
            }
        };
    }

    private static boolean bindingPossible(QuestDefinition quest, PhaseDefinition phase,
            EntryRequirementBinding binding, CollectionRecordState records) {
        CollectionEntryDefinition entry = quest.getCollectionConfig().getEntry(binding.getEntryId());
        if (entry == null) return false;
        List<Boolean> requirements = new ArrayList<>();
        for (String id : binding.getObjectiveIds()) {
            ObjectiveEntry objective = phase.getObjectives().stream().filter(o -> id.equals(o.getObjectiveId())).findFirst().orElse(null);
            if (objective != null && objective.isOptional()) continue;
            requirements.add(objective != null && !objective.getType().equals(ObjectiveType.NULL) && objective.getRequiredCount() > 0);
        }
        for (CollectionRecordRequirement requirement : binding.getRecordRequirements()) {
            requirements.add(switch (requirement.type()) {
                case DISCOVERED -> binding.getRecordPolicy() != CollectionRecordPolicy.NEW_DISCOVERIES
                        || !records.isDiscovered(binding.getEntryId());
                case RESEARCH_COMPLETE -> true;
                case RESEARCH_STEP -> entry.getResearchObjectives().stream()
                        .anyMatch(o -> requirement.stepId().equals(o.getObjectiveId()));
            });
        }
        return !requirements.isEmpty() && (binding.getRequirementMode() == CollectionRequirementMode.ANY
                ? requirements.stream().anyMatch(Boolean::booleanValue)
                : requirements.stream().allMatch(Boolean::booleanValue));
    }
}
