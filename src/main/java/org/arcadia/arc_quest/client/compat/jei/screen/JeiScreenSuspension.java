package org.arcadia.arc_quest.client.compat.jei.screen;

/** A one-shot query handoff, never a general exemption from screen cleanup. */
public final class JeiScreenSuspension {
    private boolean armed;
    private boolean suspended;
    public void arm() { armed = true; }
    public boolean removed() {
        suspended = armed;
        armed = false;
        return suspended;
    }
    public boolean resume() {
        boolean result = suspended;
        suspended = false;
        return result;
    }
    public void cancel() { armed = false; suspended = false; }
}
