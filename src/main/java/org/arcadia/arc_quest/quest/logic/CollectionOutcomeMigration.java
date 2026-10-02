package org.arcadia.arc_quest.quest.logic;

import org.arcadia.arc_quest.quest.api.CollectionEntryDefinition;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.data.CollectionRecordState;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector;

/** Explicit, threshold-preserving conversion of old completed research; never invents new run actions. */
public final class CollectionOutcomeMigration {
    private CollectionOutcomeMigration() {}

    public static boolean migrate(CollectionRecordState records, CollectionEntryDefinition entry) {
        if (!entry.isUnifiedGameplay() || records.isEntryReset(entry.getEntryId())
                || !records.isDiscovered(entry.getEntryId())) return false;
        boolean changed = false;
        for (var mapping : entry.getLegacyResearchOutcomeMappings().entrySet()) {
            boolean complete;
            if (CollectionEntryDefinition.LEGACY_RESEARCH_COMPLETE.equals(mapping.getKey())) {
                var required = entry.getLegacyResearchObjectives().stream().filter(o -> !o.isOptional()).toList();
                complete = !required.isEmpty() && required.stream().allMatch(o -> attained(records, entry, o));
            } else {
                ObjectiveEntry step = entry.getLegacyResearchObjectives().stream()
                        .filter(o -> o.getObjectiveId().equals(mapping.getKey())).findFirst().orElse(null);
                complete = step != null && attained(records, entry, step);
            }
            if (complete) changed |= records.recordOutcome(entry.getEntryId(), mapping.getValue(),
                    "legacy-research/" + mapping.getKey(), records.getGeneration(entry.getEntryId()));
        }
        return changed;
    }

    private static boolean attained(CollectionRecordState records, CollectionEntryDefinition entry, ObjectiveEntry objective) {
        return records.getProgress(entry.getEntryId(), CollectionProgressProjector.researchKey(objective.getObjectiveId()))
                >= objective.getRequiredCount();
    }
}
