package org.arcadia.arc_quest.quest.logic;

import org.arcadia.arc_quest.quest.api.CollectionEntryDefinition;
import org.arcadia.arc_quest.quest.api.EntryRequirementBinding;
import org.arcadia.arc_quest.quest.data.CollectionRecordState;
import org.arcadia.arc_quest.quest.data.CollectionRuntimeData;

/** Permanent results of authorized binding completion; deliberately owns no objective counters. */
public final class CollectionOutcomeService {
    private CollectionOutcomeService() {}

    public static boolean recordCompletion(CollectionRecordState records, CollectionRuntimeData run,
            CollectionEntryDefinition entry, EntryRequirementBinding binding, String questId, String phaseId) {
        var pending = run.consumeOutcomePending(phaseId, binding.getBindingId());
        if (pending.isEmpty() || pending.getAsLong() != records.getGeneration(entry.getEntryId())
                || !entry.isUnifiedGameplay() || !run.isBindingComplete(phaseId, binding.getBindingId())
                || !records.isDiscovered(entry.getEntryId())) return false;
        boolean changed = false;
        String source = questId + "/" + run.getRunId() + "/" + phaseId + "/" + binding.getBindingId();
        for (String id : binding.getOutcomeIds()) {
            if (entry.getOutcome(id) != null)
                changed |= records.recordOutcome(entry.getEntryId(), id, source, pending.getAsLong());
        }
        return changed;
    }
}
