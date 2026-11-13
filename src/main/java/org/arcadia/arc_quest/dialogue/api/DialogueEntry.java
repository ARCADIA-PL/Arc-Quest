package org.arcadia.arc_quest.dialogue.api;

import java.util.Objects;

/** Ordered, per-player entry routing. Resolving an entry never executes an action. */
public record DialogueEntry(String nodeId, DialogueCondition condition, String bindingId) {
    public DialogueEntry(String nodeId, DialogueCondition condition) { this(nodeId, condition, null); }
    public DialogueEntry {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(condition, "condition");
        if (nodeId.isBlank()) throw new IllegalArgumentException("Blank dialogue entry node");
    }
}
