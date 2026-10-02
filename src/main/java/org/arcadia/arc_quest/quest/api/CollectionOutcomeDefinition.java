package org.arcadia.arc_quest.quest.api;

import net.minecraft.network.chat.Component;
import java.util.Objects;

/** A permanent result of an explicitly configured investigation. Contains no objective counters. */
public record CollectionOutcomeDefinition(String outcomeId, QuestText displayName) {
    public CollectionOutcomeDefinition {
        if (outcomeId == null || outcomeId.isBlank() || outcomeId.length() > 128 || !outcomeId.equals(outcomeId.trim()))
            throw new IllegalArgumentException("Outcome requires a stable outcomeId of 1..128 characters");
        displayName = Objects.requireNonNull(displayName, "outcome displayName");
    }
    public Component getDisplayName() { return displayName.resolve(null, QuestTextContext.empty()); }
}
