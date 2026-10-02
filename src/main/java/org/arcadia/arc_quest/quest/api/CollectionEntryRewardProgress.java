package org.arcadia.arc_quest.quest.api;

public record CollectionEntryRewardProgress(CollectionEntryRewardDefinition definition, boolean unlocked, boolean claimed, String sourceRunId) {
    public CollectionEntryRewardProgress {
        sourceRunId = sourceRunId == null ? "" : sourceRunId;
    }
    public CollectionEntryRewardProgress(CollectionEntryRewardDefinition definition, boolean unlocked, boolean claimed) {
        this(definition, unlocked, claimed, "");
    }
    public boolean canClaim() { return unlocked && !claimed && definition.grantMode() == EntryRewardGrantMode.MANUAL; }
}
