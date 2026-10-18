package org.arcadia.arc_quest.dialogue.api;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** A dialogue's single-sample submission catalogue. Actions own the atomic payment. */
public record DialogueSubmissionDefinition(String id, Component title, List<Entry> entries) {
    public DialogueSubmissionDefinition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(title);
        entries = List.copyOf(entries);
        if (entries.size() > 128 || entries.stream().map(Entry::id).distinct().count() != entries.size())
            throw new IllegalArgumentException("Invalid submission entries: " + id);
    }

    /** Each accepted submission requires and consumes exactly one sample via its actions. */
    public record Entry(String id, String itemId, Predicate<ServerPlayer> available,
                        List<DialogueAction> actions, String successNodeId) {
        public Entry {
            Objects.requireNonNull(id);
            Objects.requireNonNull(itemId);
            Objects.requireNonNull(available);
            actions = List.copyOf(actions);
        }
    }
}
