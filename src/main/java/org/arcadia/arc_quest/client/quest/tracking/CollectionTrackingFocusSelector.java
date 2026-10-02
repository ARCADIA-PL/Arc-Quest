package org.arcadia.arc_quest.client.quest.tracking;

import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.data.*;
import org.jetbrains.annotations.Nullable;
import java.util.function.Function;

/** Deterministic specimen selection shared by all tracking entry points. */
public final class CollectionTrackingFocusSelector {
    public record Focus(String phaseId, CollectionBindingProgress binding) {
        public String bindingId() { return binding.bindingId(); }
    }
    private CollectionTrackingFocusSelector() {}

    @Nullable
    public static Focus select(QuestDefinition quest, QuestRuntimeData runtime, @Nullable String preferredPhase,
                               @Nullable String bindingId, Function<String, CollectionSheetProgress> sheets) {
        if (quest == null || runtime == null || runtime.getState() != QuestState.ACTIVE || !quest.hasCollectionSheets()) return null;
        var preferred = quest.getPhase(preferredPhase);
        if (preferred != null) {
            Focus selected = selectPhase(preferred, runtime, bindingId, sheets);
            if (selected != null) return selected;
        }
        for (var phase : quest.getAllPhases()) {
            if (phase == preferred) continue;
            Focus selected = selectPhase(phase, runtime, null, sheets);
            if (selected != null) return selected;
        }
        return null;
    }

    @Nullable
    private static Focus selectPhase(PhaseDefinition phase, QuestRuntimeData runtime, @Nullable String id,
                                     Function<String, CollectionSheetProgress> sheets) {
        if (!phase.hasCollectionSheet() || !runtime.isPhaseActive(phase.getPhaseId())
                || runtime.isPhasePendingManualAdvance(phase.getPhaseId())) return null;
        CollectionSheetProgress sheet = sheets.apply(phase.getPhaseId());
        if (sheet == null || sheet.complete()) return null;
        if (id != null) {
            var requested = sheet.binding(id);
            if (actionable(requested)) return new Focus(phase.getPhaseId(), requested);
        }
        for (var binding : sheet.bindings()) if (actionable(binding)) return new Focus(phase.getPhaseId(), binding);
        return null;
    }

    public static boolean actionable(@Nullable CollectionBindingProgress binding) {
        return binding != null && binding.visible() && binding.revealed() && !binding.complete()
                && binding.requirements().stream().anyMatch(requirement -> !requirement.complete()
                && (requirement.objective() == null || !requirement.objective().isHidden()));
    }

    public static String runId(QuestRuntimeData runtime) {
        String id = runtime.getCollectionData() == null ? "" : runtime.getCollectionData().getRunId();
        return id == null || id.isBlank() ? runtime.getAcceptedAtRealMs() + ":" + runtime.getAcceptedAtTick() : id;
    }
}
