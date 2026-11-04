package org.arcadia.arc_quest.quest.logic;

import org.arcadia.arc_quest.quest.api.CollectionEntryDefinition;
import org.arcadia.arc_quest.quest.api.EntryRequirementBinding;
import org.arcadia.arc_quest.quest.data.CollectionRecordState;
import org.arcadia.arc_quest.quest.data.CollectionRuntimeData;

/** Permanent results of authorized binding completion; deliberately owns no objective counters. */
public final class CollectionOutcomeService {
    private CollectionOutcomeService() {}

    /** Only the server inventory observer can enter here; no client packet supplies a fact. */
    static boolean recordObservation(
            CollectionRecordState records,
            CollectionEntryDefinition entry,
            org.arcadia.arc_quest.quest.api.CollectionObservationRule rule,
            net.minecraft.world.item.ItemStack actual,
            long generation,
            boolean freshAcquisition) {
        if (generation != records.getGeneration(entry.getEntryId())
                || !entry.getObservationRules().contains(rule)
                || records.isEntryReset(entry.getEntryId()) && !freshAcquisition) return false;
        var preview = rule.subject().resolveStack();
        if (preview.isEmpty() || !rule.subject().identity().matches(preview, actual)) return false;
        boolean changed = records.discover(entry.getEntryId());
        if (!rule.outcomeId().isEmpty() && entry.getOutcome(rule.outcomeId()) != null)
            changed |=
                    records.recordOutcome(
                            entry.getEntryId(),
                            rule.outcomeId(),
                            "observation/" + rule.ruleId() + "/v" + rule.ruleVersion(),
                            generation);
        return changed;
    }

    public static boolean recordCompletion(
            CollectionRecordState records,
            CollectionRuntimeData run,
            CollectionEntryDefinition entry,
            EntryRequirementBinding binding,
            String questId,
            String phaseId) {
        var pending = run.consumeOutcomePending(phaseId, binding.getBindingId());
        if (pending.isEmpty()
                || pending.getAsLong() != records.getGeneration(entry.getEntryId())
                || !entry.isUnifiedGameplay()
                || !run.isBindingComplete(phaseId, binding.getBindingId())
                || !records.isDiscovered(entry.getEntryId())) return false;
        boolean changed = false;
        String source =
                questId + "/" + run.getRunId() + "/" + phaseId + "/" + binding.getBindingId();
        for (String id : binding.getOutcomeIds()) {
            if (entry.getOutcome(id) != null)
                changed |=
                        records.recordOutcome(entry.getEntryId(), id, source, pending.getAsLong());
        }
        return changed;
    }
}
