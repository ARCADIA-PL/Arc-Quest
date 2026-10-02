package org.arcadia.arc_quest.quest.api;

import net.minecraft.resources.ResourceLocation;
import java.util.Objects;

/** Stable source path for an explicitly authorized investigation; never a copied objective group. */
public record CollectionOutcomeSource(ResourceLocation questId, String phaseId, String bindingId) {
    public CollectionOutcomeSource {
        Objects.requireNonNull(questId, "outcome source questId");
        if (phaseId == null || phaseId.isBlank() || bindingId == null || bindingId.isBlank())
            throw new IllegalArgumentException("Outcome source requires phaseId and bindingId");
    }
}
