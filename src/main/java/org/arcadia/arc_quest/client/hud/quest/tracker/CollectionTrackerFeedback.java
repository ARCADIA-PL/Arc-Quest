package org.arcadia.arc_quest.client.hud.quest.tracker;

import java.util.Objects;

/** Timed presentation never changes authoritative completion or emits notifications on reconnect. */
public final class CollectionTrackerFeedback {
    private String context = "", focus = "";
    private boolean wasReady, wasFocusComplete;
    private long readyAt = -1, focusAt = -1;

    public record Result(boolean keepFocus, boolean focusComplete, boolean readyMessage, boolean hide) {}

    public Result update(String contextId, String focusId, boolean ready, boolean focusComplete, long now) {
        if (!Objects.equals(context, contextId)) {
            context = contextId; focus = ""; wasReady = ready;
            readyAt = ready ? now - 3500 : -1;
        } else if (ready && !wasReady) readyAt = now;
        else if (!ready) readyAt = -1;
        wasReady = ready;
        boolean initialCompletedFocus = false;
        if (!Objects.equals(focus, focusId)) {
            focus = focusId; wasFocusComplete = focusComplete;
            focusAt = -1;
            initialCompletedFocus = focusComplete;
        } else if (focusComplete && !wasFocusComplete) focusAt = now;
        else if (!focusComplete) focusAt = -1;
        wasFocusComplete = focusComplete;
        boolean finishing = focusComplete && focusAt >= 0 && now - focusAt < 1200;
        boolean keepFocus = !focusId.isEmpty() && (!focusComplete || finishing) && !initialCompletedFocus;
        boolean showReady = ready && readyAt >= 0 && now - readyAt < 3500;
        return new Result(keepFocus, finishing, showReady, ready && !showReady && !finishing);
    }

    public void reset() { context = ""; focus = ""; wasReady = false; wasFocusComplete = false; readyAt = -1; focusAt = -1; }
}
