package org.arcadia.arc_quest.client.hud.gacha;

/** A client timeout is a notification, never evidence that a server transaction failed. */
final class GachaDrawRequestState {
    private boolean pending, waiting, slowNoticeSent;
    private long started;

    boolean start(long now) {
        if (pending) return false;
        pending = waiting = true;
        slowNoticeSent = false;
        started = now;
        return true;
    }
    boolean pending() { return pending; }
    boolean pollSlowNotice(long now, long timeout) {
        if (!waiting || !pending || slowNoticeSent || now - started < timeout) return false;
        slowNoticeSent = true;
        return true;
    }
    boolean acceptResult() {
        if (!waiting || !pending) return false;
        waiting = false;
        return true;
    }
    boolean fail() {
        if (!waiting || !pending) return false;
        pending = waiting = false;
        return true;
    }
    boolean confirm() {
        if (!pending) return false;
        pending = waiting = false;
        return true;
    }
}
