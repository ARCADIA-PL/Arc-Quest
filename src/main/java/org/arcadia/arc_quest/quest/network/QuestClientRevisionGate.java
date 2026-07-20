package org.arcadia.arc_quest.quest.network;

public final class QuestClientRevisionGate {

    private long playerSessionEpoch = -1L;
    private long revision = -1L;
    private boolean resyncRequested;

    public synchronized Decision acceptSnapshot(long epoch, long snapshotRevision) {
        if (isLegacy(epoch, snapshotRevision)) return Decision.ACCEPT;
        if (epoch < playerSessionEpoch) return Decision.STALE;
        if (epoch == playerSessionEpoch && snapshotRevision < revision) return Decision.STALE;
        playerSessionEpoch = epoch;
        revision = snapshotRevision;
        resyncRequested = false;
        return Decision.ACCEPT;
    }

    public synchronized Decision acceptDelta(long epoch, long baseRevision, long newRevision) {
        if (isLegacy(epoch, newRevision)) return Decision.ACCEPT;
        if (epoch < playerSessionEpoch || (epoch == playerSessionEpoch && newRevision <= revision)) {
            return Decision.STALE;
        }
        if (epoch > playerSessionEpoch || revision < 0L || baseRevision != revision || newRevision != baseRevision + 1L) {
            if (resyncRequested) return Decision.RESYNC_PENDING;
            resyncRequested = true;
            return Decision.GAP;
        }
        revision = newRevision;
        return Decision.ACCEPT;
    }

    public synchronized long playerSessionEpoch() {
        return playerSessionEpoch;
    }

    public synchronized long revision() {
        return revision;
    }

    public synchronized void clear() {
        playerSessionEpoch = -1L;
        revision = -1L;
        resyncRequested = false;
    }

    private boolean isLegacy(long epoch, long packetRevision) {
        return epoch <= 0L || packetRevision <= 0L;
    }

    public enum Decision {
        ACCEPT,
        STALE,
        GAP,
        RESYNC_PENDING
    }
}
