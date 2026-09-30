package org.arcadia.arc_quest.client.data.sync;

/** Tracks fully committed content and bounded retries without depending on a Minecraft runtime. */
final class ClientContentSyncState {
    enum HeaderResult { APPLY, ALREADY_APPLIED, STALE }
    enum RetryAction { NONE, REQUEST, EXHAUSTED }
    private static final long[] RETRY_DELAYS_NANOS = {1_000_000_000L, 2_000_000_000L, 4_000_000_000L};
    private long appliedEpoch = -1L;
    private String appliedHash = "";
    private long targetEpoch = -1L;
    private String targetHash = "";
    private int retryAttempts;
    private long retryAtNanos;
    private boolean retryScheduled;
    private boolean exhaustionReported;

    long appliedEpoch() { return appliedEpoch; }
    String appliedHash() { return appliedHash; }
    long targetEpoch() { return targetEpoch; }

    boolean isStale(long epoch) { return epoch < 0 || epoch < appliedEpoch || epoch < targetEpoch; }

    HeaderResult begin(long epoch, String hash) {
        if (isStale(epoch)) return HeaderResult.STALE;
        if (epoch == appliedEpoch && hash.equals(appliedHash)) {
            resetRetries();
            return HeaderResult.ALREADY_APPLIED;
        }
        if (epoch != targetEpoch || !hash.equals(targetHash)) {
            targetEpoch = epoch;
            targetHash = hash;
            resetRetries();
        } else {
            // Receiving an identical retry must not restore its already-used retry budget.
            retryScheduled = false;
        }
        return HeaderResult.APPLY;
    }

    boolean complete(long epoch, String hash, boolean fullyApplied, long nowNanos) {
        if (epoch != targetEpoch || !hash.equals(targetHash)) return false;
        if (!fullyApplied) {
            scheduleRetry(nowNanos);
            return false;
        }
        appliedEpoch = epoch;
        appliedHash = hash;
        resetRetries();
        return true;
    }

    void expect(long epoch, long nowNanos) {
        if (epoch < 0 || epoch <= appliedEpoch || epoch < targetEpoch) return;
        if (epoch > targetEpoch) {
            targetEpoch = epoch;
            targetHash = "";
            resetRetries();
        }
        scheduleRetry(nowNanos);
    }

    void scheduleRetry(long nowNanos) {
        if (retryScheduled || exhaustionReported) return;
        retryScheduled = true;
        retryAtNanos = nowNanos + (retryAttempts < RETRY_DELAYS_NANOS.length
                ? RETRY_DELAYS_NANOS[retryAttempts] : 0L);
    }

    RetryAction pollRetry(long nowNanos) {
        if (!retryScheduled || nowNanos - retryAtNanos < 0L) return RetryAction.NONE;
        if (retryAttempts >= RETRY_DELAYS_NANOS.length) {
            retryScheduled = false;
            exhaustionReported = true;
            return RetryAction.EXHAUSTED;
        }
        retryAttempts++;
        // Keep a deadline if a request is throttled or its response never arrives.
        retryAtNanos = nowNanos + RETRY_DELAYS_NANOS[Math.min(retryAttempts, RETRY_DELAYS_NANOS.length - 1)];
        return RetryAction.REQUEST;
    }

    void clear() {
        appliedEpoch = targetEpoch = -1L;
        appliedHash = targetHash = "";
        resetRetries();
    }

    private void resetRetries() {
        retryAttempts = 0;
        retryAtNanos = 0L;
        retryScheduled = exhaustionReported = false;
    }
}
