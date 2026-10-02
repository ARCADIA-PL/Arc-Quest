package org.arcadia.arc_quest.quest.api;

import java.util.Objects;

/** A reference to a permanent fact, never a copy of a task objective counter. */
public record CollectionRecordRequirement(Type type, String stepId) {
    public enum Type { DISCOVERED, RESEARCH_COMPLETE, RESEARCH_STEP, OUTCOME }

    public CollectionRecordRequirement {
        Objects.requireNonNull(type, "record requirement type");
        stepId = stepId == null ? "" : stepId.trim();
        if ((type == Type.RESEARCH_STEP || type == Type.OUTCOME) && stepId.isBlank()) {
            throw new IllegalArgumentException("Research-step/outcome requires a stable stepId");
        }
        if (type != Type.RESEARCH_STEP && type != Type.OUTCOME && !stepId.isEmpty()) {
            throw new IllegalArgumentException("stepId is only valid for RESEARCH_STEP or OUTCOME");
        }
    }

    public static CollectionRecordRequirement discovered() {
        return new CollectionRecordRequirement(Type.DISCOVERED, "");
    }

    public static CollectionRecordRequirement researched() {
        return new CollectionRecordRequirement(Type.RESEARCH_COMPLETE, "");
    }

    public static CollectionRecordRequirement researchStep(String stepId) {
        return new CollectionRecordRequirement(Type.RESEARCH_STEP, stepId);
    }

    public static CollectionRecordRequirement outcome(String outcomeId) {
        return new CollectionRecordRequirement(Type.OUTCOME, outcomeId);
    }
}
