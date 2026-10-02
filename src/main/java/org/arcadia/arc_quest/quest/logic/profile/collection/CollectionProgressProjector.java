package org.arcadia.arc_quest.quest.logic.profile.collection;

import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.data.*;

import java.util.*;

/** Shared, side-effect-free completion and disclosure projection for server and client. */
public final class CollectionProgressProjector {
    private CollectionProgressProjector() {}

    public static CollectionSheetProgress project(QuestDefinition quest, PhaseDefinition phase,
                                                   QuestRuntimeData runtime, CollectionRecordState records) {
        return project(quest, phase, runtime, records, null);
    }

    public static CollectionSheetProgress project(QuestDefinition quest, PhaseDefinition phase,
            QuestRuntimeData runtime, CollectionRecordState records, CollectionQuestArchives archives) {
        CollectionSheetDefinition sheet = phase.getCollectionSheet();
        CollectionQuestConfig config = quest.getCollectionConfig();
        if (sheet == null || config == null) return CollectionSheetProgress.EMPTY;
        CollectionRuntimeData run = runtime == null ? null : runtime.getCollectionData();
        List<String> frozen = run == null ? List.of() : run.getFrozenBindingIds(phase.getPhaseId());
        if (run != null && !run.getRunId().isEmpty() && frozen.isEmpty())
            return new CollectionSheetProgress(0, sheet.getRequiredCount(), 0, false, List.of(), List.of());
        List<String> ids = frozen.isEmpty() ? sheet.getBindings().stream().map(EntryRequirementBinding::getBindingId).toList() : frozen;
        List<CollectionBindingProgress> rows = new ArrayList<>();
        Map<String, Boolean> candidates = new LinkedHashMap<>();
        Map<String, Map<String, Boolean>> categories = new LinkedHashMap<>();
        for (String id : ids) {
            EntryRequirementBinding binding = sheet.getBinding(id);
            // A removed frozen requirement remains unsatisfied; a reload cannot silently award it.
            if (binding == null) { candidates.put("missing:" + id, false); continue; }
            CollectionEntryDefinition entry = config.getEntry(binding.getEntryId());
            if (entry == null) { candidates.put("missing:" + id, false); continue; }
            CollectionBindingProgress row = binding(quest, phase, runtime, run, records, binding, entry, archives);
            rows.add(row);
            if (!binding.isOptional()) {
                String key = sheet.isCountDistinctEntries() ? binding.getEntryId().toString() : id;
                candidates.merge(key, row.complete(), (a, b) -> a && b);
                categories.computeIfAbsent(entry.getCategoryId(), ignored -> new LinkedHashMap<>())
                        .merge(key, row.complete(), (a, b) -> a && b);
            }
        }
        int target = run == null ? sheet.getRequiredCount() : run.getFrozenSheetTarget(phase.getPhaseId(), sheet.getRequiredCount());
        int completed = (int) candidates.values().stream().filter(Boolean::booleanValue).count();
        List<CollectionCategoryProgress> categoryRows = new ArrayList<>();
        categories.forEach((id, values) -> categoryRows.add(new CollectionCategoryProgress(id,
                (int) values.values().stream().filter(Boolean::booleanValue).count(), values.size())));
        return new CollectionSheetProgress(Math.min(completed, target), target, candidates.size(), completed >= target,
                rows, categoryRows);
    }

    private static CollectionBindingProgress binding(QuestDefinition quest, PhaseDefinition phase, QuestRuntimeData runtime,
            CollectionRuntimeData run, CollectionRecordState records, EntryRequirementBinding binding,
            CollectionEntryDefinition entry, CollectionQuestArchives archives) {
        boolean discovered = records.isDiscovered(entry.getEntryId());
        boolean researched = researchComplete(entry, records);
        boolean revealed = discovered || entry.getVisibilityMode() == VisibilityMode.VISIBLE_BY_DEFAULT;
        boolean visible = revealed || entry.getHiddenPresentationMode() != HiddenPresentationMode.FULLY_HIDDEN;
        List<CollectionRequirementProgress> requirements = new ArrayList<>();
        for (String objectiveId : binding.getObjectiveIds()) {
            int index = -1;
            for (int i = 0; i < phase.getObjectives().size(); i++)
                if (objectiveId.equals(phase.getObjectives().get(i).getObjectiveId())) { index = i; break; }
            if (index < 0) {
                requirements.add(new CollectionRequirementProgress(objectiveId,
                        Component.translatable("arc_quest.collection.requirement.unavailable"), null, -1, 0, 1, false, false));
                continue;
            }
            ObjectiveEntry objective = phase.getObjectives().get(index);
            int target = runtime == null ? objective.getRequiredCount()
                    : runtime.getRequiredCount(phase.getPhaseId(), index, objective.getRequiredCount());
            int current = runtime == null ? 0 : runtime.getObjectiveProgress(phase.getPhaseId(), index);
            requirements.add(new CollectionRequirementProgress(objectiveId, objective.getDisplayText(), objective,
                    index, Math.min(current, target), target, current >= target, objective.isOptional()));
        }
        for (CollectionRecordRequirement requirement : binding.getRecordRequirements()) {
            int target = 1;
            int current = 0;
            Component label;
            switch (requirement.type()) {
                case DISCOVERED -> {
                    boolean fresh = binding.getRecordPolicy() != CollectionRecordPolicy.NEW_DISCOVERIES
                            || (run != null && !run.wasDiscoveredAtAccept(phase.getPhaseId(), entry.getEntryId().toString()));
                    current = discovered && fresh ? 1 : 0;
                    label = Component.translatable(binding.getRecordPolicy() == CollectionRecordPolicy.NEW_DISCOVERIES
                            ? "arc_quest.collection.requirement.new_discovery" : "arc_quest.collection.requirement.discovered");
                }
                case RESEARCH_COMPLETE -> {
                    current = researched ? 1 : 0;
                    label = Component.translatable("arc_quest.collection.requirement.researched");
                }
                case RESEARCH_STEP -> {
                    ObjectiveEntry step = entry.getResearchObjectives().stream()
                            .filter(o -> requirement.stepId().equals(o.getObjectiveId())).findFirst().orElse(null);
                    target = step == null ? 1 : step.getRequiredCount();
                    current = step == null ? 0 : records.getProgress(entry.getEntryId(), researchKey(step.getObjectiveId()));
                    label = step == null ? Component.translatable("arc_quest.collection.requirement.unavailable") : step.getDisplayText();
                }
                default -> throw new IllegalStateException("Unknown collection record requirement");
            }
            requirements.add(new CollectionRequirementProgress("record:" + requirement.type() + ":" + requirement.stepId(),
                    label, null, -1, Math.min(current, target), target, current >= target, false));
        }
        List<CollectionRequirementProgress> required = requirements.stream().filter(r -> !r.optional()).toList();
        boolean complete = !required.isEmpty() && (binding.getRequirementMode() == CollectionRequirementMode.ANY
                ? required.stream().anyMatch(CollectionRequirementProgress::complete)
                : required.stream().allMatch(CollectionRequirementProgress::complete));
        if (run != null) {
            boolean latched = run.isBindingComplete(phase.getPhaseId(), binding.getBindingId());
            // Knowledge keeps growing after a run ends; its result remains the
            // set of requirements actually attained during that run.
            complete = runtime.getState() == QuestState.ACTIVE ? complete || latched : latched;
        }
        List<CollectionContentBlock> content = revealed ? entry.getContent().stream()
                .filter(block -> contentRevealed(entry, block, records)).toList() : List.of();
        List<CollectionEntryRewardProgress> entryRewards = new ArrayList<>();
        if (revealed) {
            entryRewards.addAll(org.arcadia.arc_quest.quest.logic.CollectionEntryRewardService.project(quest, phase, runtime, records, binding));
            entryRewards.addAll(org.arcadia.arc_quest.quest.logic.CollectionEntryRewardService.pendingPriorRewards(quest, phase, binding, runtime, records, archives));
        }
        return new CollectionBindingProgress(binding.getBindingId(), entry.getEntryId(), visible, revealed, discovered,
                researched, complete, revealed ? requirements : List.of(), content,
                entryRewards);
    }

    public static boolean researchComplete(CollectionEntryDefinition entry, CollectionRecordState records) {
        return records.isDiscovered(entry.getEntryId()) && entry.getResearchObjectives().stream()
                .filter(o -> !o.isOptional())
                .allMatch(o -> records.getProgress(entry.getEntryId(), researchKey(o.getObjectiveId())) >= o.getRequiredCount());
    }

    public static boolean contentRevealed(CollectionEntryDefinition entry, CollectionContentBlock block, CollectionRecordState records) {
        return switch (block.reveal()) {
            case ALWAYS -> true;
            case DISCOVERED -> records.isDiscovered(entry.getEntryId());
            case RESEARCH_COMPLETE -> researchComplete(entry, records);
            case RESEARCH_STEP -> entry.getResearchObjectives().stream().anyMatch(o -> o.getObjectiveId().equals(block.revealStepId())
                    && records.getProgress(entry.getEntryId(), researchKey(o.getObjectiveId())) >= o.getRequiredCount());
        };
    }

    public static String discoveryKey(String id) { return "discovery:" + id; }
    public static String researchKey(String id) { return "research:" + id; }
}
