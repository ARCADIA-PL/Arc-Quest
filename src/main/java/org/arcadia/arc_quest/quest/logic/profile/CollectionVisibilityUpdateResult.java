package org.arcadia.arc_quest.quest.logic.profile;

import javax.annotation.Nullable;

public final class CollectionVisibilityUpdateResult {

    private final Status status;
    @Nullable
    private final String phaseId;
    private final boolean changed;
    private final boolean visible;
    private final boolean discovered;

    private CollectionVisibilityUpdateResult(Status status,
                                             @Nullable String phaseId,
                                             boolean changed,
                                             boolean visible,
                                             boolean discovered) {
        this.status = status;
        this.phaseId = phaseId;
        this.changed = changed;
        this.visible = visible;
        this.discovered = discovered;
    }

    public static CollectionVisibilityUpdateResult ok(@Nullable String phaseId, boolean visible, boolean discovered) {
        return new CollectionVisibilityUpdateResult(Status.OK, phaseId, true, visible, discovered);
    }

    public static CollectionVisibilityUpdateResult unchanged(Status status, @Nullable String phaseId) {
        return new CollectionVisibilityUpdateResult(status, phaseId, false, false, false);
    }

    public Status getStatus() {
        return status;
    }

    @Nullable
    public String getPhaseId() {
        return phaseId;
    }

    public boolean isChanged() {
        return changed;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean isDiscovered() {
        return discovered;
    }

    public enum Status {
        OK,
        NO_COLLECTION_DATA,
        PHASE_NOT_FOUND,
        ENTRY_CONFIG_MISSING,
        NO_VISIBILITY_CHANGE
    }
}
