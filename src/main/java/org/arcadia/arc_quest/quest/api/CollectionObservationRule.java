package org.arcadia.arc_quest.quest.api;

/** Executable server rule, deliberately separate from presentation identities. */
public record CollectionObservationRule(
        String ruleId, int ruleVersion, CollectionSubjectSpec subject, String outcomeId) {
    public CollectionObservationRule {
        if (ruleId == null
                || ruleId.isBlank()
                || ruleId.length() > 128
                || ruleVersion < 1
                || subject == null) throw new IllegalArgumentException("Invalid observation rule");
        outcomeId = outcomeId == null ? "" : outcomeId;
    }
}
