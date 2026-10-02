package org.arcadia.arc_quest.quest.logic.profile.collection;

import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.data.*;

import java.util.*;

/** Reward-only counters derived from the same binding projection as task completion. */
public final class ModernCollectionRuleStats {
    public record Counts(int total, int completed, int visible, int discovered) {
        public boolean allComplete() { return total > 0 && completed >= total; }
        public float ratio() { return total == 0 ? 0 : completed / (float) total; }
    }
    private final Counts overall;
    private final Map<String, Counts> categories;

    private ModernCollectionRuleStats(Counts overall, Map<String, Counts> categories) {
        this.overall = overall; this.categories = Map.copyOf(categories);
    }

    public static ModernCollectionRuleStats project(QuestDefinition quest, QuestRuntimeData runtime, CollectionRecordState records) {
        CollectionRuntimeData run = runtime.getCollectionData();
        Map<String, boolean[]> candidates = new LinkedHashMap<>();
        Map<String, Map<String, boolean[]>> categories = new LinkedHashMap<>();
        for (PhaseDefinition phase : quest.getAllPhases()) {
            CollectionSheetDefinition sheet = phase.getCollectionSheet();
            if (sheet == null) continue;
            CollectionSheetProgress projected = CollectionProgressProjector.project(quest, phase, runtime, records);
            List<String> frozen = run == null ? List.of() : run.getFrozenBindingIds(phase.getPhaseId());
            List<String> ids = frozen.isEmpty() ? sheet.getBindings().stream().map(EntryRequirementBinding::getBindingId).toList() : frozen;
            boolean activated = runtime.isPhaseActive(phase.getPhaseId()) || runtime.isPhaseCompleted(phase.getPhaseId());
            for (String id : ids) {
                EntryRequirementBinding binding = sheet.getBinding(id);
                if (binding == null) { candidates.put(phase.getPhaseId() + "/missing:" + id, new boolean[3]); continue; }
                if (binding.isOptional()) continue;
                CollectionEntryDefinition entry = quest.getCollectionConfig().getEntry(binding.getEntryId());
                if (entry == null) { candidates.put(phase.getPhaseId() + "/missing:" + id, new boolean[3]); continue; }
                CollectionBindingProgress row = projected.binding(id);
                boolean complete = row != null && row.complete() && activated;
                // Completed/failed/abandoned history keeps the facts latched during that run.
                if (runtime.getState() != QuestState.ACTIVE) complete = run != null && run.isBindingComplete(phase.getPhaseId(), id);
                String key = phase.getPhaseId() + "/" + (sheet.isCountDistinctEntries() ? binding.getEntryId() : id);
                boolean[] value = {complete, row != null && row.visible(), row != null && row.discovered()};
                merge(candidates, key, value);
                merge(categories.computeIfAbsent(entry.getCategoryId(), ignored -> new LinkedHashMap<>()), key, value);
            }
        }
        Map<String, Counts> categoryCounts = new LinkedHashMap<>();
        categories.forEach((id, values) -> categoryCounts.put(id, count(values)));
        return new ModernCollectionRuleStats(count(candidates), categoryCounts);
    }

    private static void merge(Map<String, boolean[]> values, String key, boolean[] next) {
        values.merge(key, next.clone(), (previous, current) -> new boolean[]{previous[0] && current[0], previous[1] || current[1], previous[2] || current[2]});
    }
    private static Counts count(Map<String, boolean[]> values) {
        int completed = 0, visible = 0, discovered = 0;
        for (boolean[] value : values.values()) { if (value[0]) completed++; if (value[1]) visible++; if (value[2]) discovered++; }
        return new Counts(values.size(), completed, visible, discovered);
    }
    public Counts overall() { return overall; }
    public Counts category(String id) { return categories.getOrDefault(id, new Counts(0, 0, 0, 0)); }
    public int completedCategories() { return (int) categories.values().stream().filter(Counts::allComplete).count(); }
    public int totalCategories() { return categories.size(); }
}
