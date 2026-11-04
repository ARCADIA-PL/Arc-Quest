package org.arcadia.arc_quest.integration.jei.api;

import java.util.Objects;

/** Stable, non-executable collection address, scoped to an authorized run. */
public record CollectionNavigationTarget(String questId, String phaseId, String bindingId, String entryId,
                                         String variantId, String runId) {
    public CollectionNavigationTarget {
        Objects.requireNonNull(questId); Objects.requireNonNull(phaseId); Objects.requireNonNull(bindingId);
        Objects.requireNonNull(entryId); Objects.requireNonNull(variantId); Objects.requireNonNull(runId);
        if (questId.isBlank() || phaseId.isBlank() || bindingId.isBlank() || entryId.isBlank())
            throw new IllegalArgumentException("Incomplete collection address");
    }
}
