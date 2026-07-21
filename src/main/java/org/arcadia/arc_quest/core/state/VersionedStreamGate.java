package org.arcadia.arc_quest.core.state;

import java.util.Objects;

public final class VersionedStreamGate {

    private final boolean acceptLegacyUntracked;
    private long epoch = -1L;
    private long revision = -1L;
    private boolean resyncRequested;

    public VersionedStreamGate() {
        this(false);
    }

    public VersionedStreamGate(boolean acceptLegacyUntracked) {
        this.acceptLegacyUntracked = acceptLegacyUntracked;
    }

    public synchronized Decision acceptSnapshot(long incomingEpoch, long snapshotRevision) {
        return applySnapshot(incomingEpoch, snapshotRevision, () -> {
        });
    }

    public synchronized Decision applySnapshot(long incomingEpoch, long snapshotRevision, Runnable application) {
        Objects.requireNonNull(application, "application");
        if (isLegacy(incomingEpoch, snapshotRevision)) {
            application.run();
            return Decision.ACCEPT;
        }
        if (incomingEpoch < epoch) return Decision.STALE;
        if (incomingEpoch == epoch && snapshotRevision < revision) return Decision.STALE;
        application.run();
        epoch = incomingEpoch;
        revision = snapshotRevision;
        resyncRequested = false;
        return Decision.ACCEPT;
    }

    public synchronized Decision acceptDelta(long incomingEpoch, long baseRevision, long newRevision) {
        return applyDelta(incomingEpoch, baseRevision, newRevision, () -> {
        });
    }

    public synchronized Decision applyDelta(long incomingEpoch, long baseRevision, long newRevision,
                                            Runnable application) {
        Objects.requireNonNull(application, "application");
        if (isLegacy(incomingEpoch, newRevision)) {
            application.run();
            return Decision.ACCEPT;
        }
        if (incomingEpoch < epoch || (incomingEpoch == epoch && newRevision <= revision)) {
            return Decision.STALE;
        }
        if (incomingEpoch > epoch || revision < 0L
                || baseRevision != revision || newRevision != baseRevision + 1L) {
            if (resyncRequested) return Decision.RESYNC_PENDING;
            resyncRequested = true;
            return Decision.GAP;
        }
        application.run();
        revision = newRevision;
        return Decision.ACCEPT;
    }

    public synchronized long epoch() {
        return epoch;
    }

    public synchronized long revision() {
        return revision;
    }

    public synchronized void clear() {
        epoch = -1L;
        revision = -1L;
        resyncRequested = false;
    }

    private boolean isLegacy(long incomingEpoch, long packetRevision) {
        return acceptLegacyUntracked && (incomingEpoch <= 0L || packetRevision <= 0L);
    }

    public enum Decision {
        ACCEPT,
        STALE,
        GAP,
        RESYNC_PENDING
    }
}
