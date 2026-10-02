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

    /** Exact entry eligibility; unlike automatic selection this never substitutes another specimen. */
    @Nullable
    public static Focus selectRequested(QuestDefinition quest, QuestRuntimeData runtime, @Nullable String phaseId,
                                        @Nullable String bindingId, Function<String, CollectionSheetProgress> sheets) {
        if (quest == null || runtime == null || runtime.getState() != QuestState.ACTIVE || !quest.hasCollectionSheets()
                || phaseId == null || bindingId == null) return null;
        var phase = quest.getPhase(phaseId);
        if (phase == null) return null;
        var sheet = eligibleSheet(phase, runtime, sheets);
        var requested = sheet == null ? null : sheet.binding(bindingId);
        return actionable(requested) ? new Focus(phaseId, requested) : null;
    }

    public static boolean canTrack(QuestDefinition quest, QuestRuntimeData runtime, @Nullable String phaseId,
                                   @Nullable String bindingId, Function<String, CollectionSheetProgress> sheets) {
        return selectRequested(quest, runtime, phaseId, bindingId, sheets) != null;
    }

    @Nullable
    private static Focus selectPhase(PhaseDefinition phase, QuestRuntimeData runtime, @Nullable String id,
                                     Function<String, CollectionSheetProgress> sheets) {
        CollectionSheetProgress sheet = eligibleSheet(phase, runtime, sheets);
        if (sheet == null) return null;
        if (id != null) {
            var requested = sheet.binding(id);
            if (actionable(requested)) return new Focus(phase.getPhaseId(), requested);
        }
        for (var binding : sheet.bindings()) if (actionable(binding)) return new Focus(phase.getPhaseId(), binding);
        return null;
    }

    @Nullable
    private static CollectionSheetProgress eligibleSheet(PhaseDefinition phase, QuestRuntimeData runtime,
                                                         Function<String, CollectionSheetProgress> sheets) {
        if (!phase.hasCollectionSheet() || !runtime.isPhaseActive(phase.getPhaseId())
                || runtime.isPhasePendingManualAdvance(phase.getPhaseId())) return null;
        CollectionSheetProgress sheet = sheets.apply(phase.getPhaseId());
        return sheet == null || sheet.complete() ? null : sheet;
    }

    public static boolean actionable(@Nullable CollectionBindingProgress binding) {
        // Record requirements deliberately have no run ObjectiveEntry. Discovery and lifetime
        // research still provide actionable progress and must not lose their tracking affordance.
        return binding != null && binding.visible() && binding.revealed() && !binding.complete()
                && binding.requirements().stream().anyMatch(requirement -> !requirement.complete()
                && (requirement.objective() == null || !requirement.objective().isHidden()));
    }

    public static String runId(QuestRuntimeData runtime) {
        String id = runtime.getCollectionData() == null ? "" : runtime.getCollectionData().getRunId();
        return id == null || id.isBlank() ? runtime.getAcceptedAtRealMs() + ":" + runtime.getAcceptedAtTick() : id;
    }
}
