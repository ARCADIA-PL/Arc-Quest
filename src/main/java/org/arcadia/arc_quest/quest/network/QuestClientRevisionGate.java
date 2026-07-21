package org.arcadia.arc_quest.quest.network;

import org.arcadia.arc_quest.core.state.VersionedStreamGate;

public final class QuestClientRevisionGate {

    private final VersionedStreamGate delegate = new VersionedStreamGate(true);

    public Decision acceptSnapshot(long epoch, long snapshotRevision) {
        return Decision.valueOf(delegate.acceptSnapshot(epoch, snapshotRevision).name());
    }

    public Decision acceptDelta(long epoch, long baseRevision, long newRevision) {
        return Decision.valueOf(delegate.acceptDelta(epoch, baseRevision, newRevision).name());
    }

    public long playerSessionEpoch() {
        return delegate.epoch();
    }

    public long revision() {
        return delegate.revision();
    }

    public void clear() {
        delegate.clear();
    }

    public enum Decision {
        ACCEPT,
        STALE,
        GAP,
        RESYNC_PENDING
    }
}
