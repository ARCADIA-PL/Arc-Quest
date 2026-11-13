package org.arcadia.arc_quest.dialogue.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A retired tree's stable entry and node identities, declared by its native successor. */
public record DialogueIdentityMigration(String dialogueId, String startNodeId,
                                        Map<String, String> nodes, List<DialogueEntry> entries) {
    public DialogueIdentityMigration {
        Objects.requireNonNull(dialogueId, "dialogueId");
        Objects.requireNonNull(startNodeId, "startNodeId");
        if (dialogueId.isBlank() || startNodeId.isBlank())
            throw new IllegalArgumentException("Blank legacy dialogue identity");
        nodes = Map.copyOf(nodes);
        entries = List.copyOf(entries);
        if (nodes.entrySet().stream().anyMatch(e -> e.getKey().isBlank() || e.getValue().isBlank()))
            throw new IllegalArgumentException("Blank legacy node identity");
    }

    public DialogueIdentityMigration(String dialogueId, String startNodeId, Map<String, String> nodes) {
        this(dialogueId, startNodeId, nodes, List.of());
    }

    public String resolveNode(String oldNodeId) {
        return nodes.getOrDefault(oldNodeId, oldNodeId);
    }
}
