package org.arcadia.arc_quest.quest.logic.profile;

import javax.annotation.Nullable;

public final class CollectionEntryUpdateResult {

    private final Status status;
    @Nullable
    private final String phaseId;
    private final int nextCount;
    private final boolean changed;
    private final boolean entryCompleted;
    private final boolean questCompleted;
    private CollectionEntryUpdateResult(Status status,
                                        @Nullable String phaseId,
                                        int nextCount,
                                        boolean changed,
                                        boolean entryCompleted,
                                        boolean questCompleted) {
        this.status = status;
        this.phaseId = phaseId;
        this.nextCount = Math.max(0, nextCount);
        this.changed = changed;
        this.entryCompleted = entryCompleted;
        this.questCompleted = questCompleted;
    }

    public static CollectionEntryUpdateResult ok(@Nullable String phaseId,
                                                 int nextCount,
                                                 boolean entryCompleted,
                                                 boolean questCompleted) {
        return new CollectionEntryUpdateResult(Status.OK, phaseId, nextCount, true, entryCompleted, questCompleted);
    }

    public static CollectionEntryUpdateResult unchanged(Status status, @Nullable String phaseId) {
        return new CollectionEntryUpdateResult(status, phaseId, 0, false, false, false);
    }

    public Status getStatus() {
        return status;
    }

    @Nullable
    public String getPhaseId() {
        return phaseId;
    }

    public int getNextCount() {
        return nextCount;
    }

    public boolean isChanged() {
        return changed;
    }

    public boolean isEntryCompleted() {
        return entryCompleted;
    }

    public boolean isQuestCompleted() {
        return questCompleted;
    }

    public enum Status {
        OK,
        INVALID_AMOUNT,
        INVALID_UNIQUE_KEY,
        NO_COLLECTION_DATA,
        PHASE_NOT_FOUND,
        ENTRY_CONFIG_MISSING,
        DUPLICATE_UNIQUE_KEY,
        NOT_CHANGED
    }
}
