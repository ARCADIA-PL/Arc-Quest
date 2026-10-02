package org.arcadia.arc_quest.quest.api;

public record CollectionEntryRewardProgress(CollectionEntryRewardDefinition definition, boolean unlocked, boolean claimed, String sourceRunId,
                                           String sourcePhaseId, String sourceBindingId, boolean deliveryPending) {
    public CollectionEntryRewardProgress {
        sourceRunId = sourceRunId == null ? "" : sourceRunId;
        sourcePhaseId = sourcePhaseId == null ? "" : sourcePhaseId;
        sourceBindingId = sourceBindingId == null ? "" : sourceBindingId;
    }
    public CollectionEntryRewardProgress(CollectionEntryRewardDefinition definition, boolean unlocked, boolean claimed, String sourceRunId) {
        this(definition, unlocked, claimed, sourceRunId, "", "", false);
    }
    public CollectionEntryRewardProgress(CollectionEntryRewardDefinition definition, boolean unlocked, boolean claimed, String sourceRunId, String sourcePhaseId, String sourceBindingId) {
        this(definition, unlocked, claimed, sourceRunId, sourcePhaseId, sourceBindingId, false);
    }
    public CollectionEntryRewardProgress(CollectionEntryRewardDefinition definition, boolean unlocked, boolean claimed) {
        this(definition, unlocked, claimed, "");
    }
    public boolean canClaim() { return unlocked && !claimed && definition.grantMode() == EntryRewardGrantMode.MANUAL; }
}
