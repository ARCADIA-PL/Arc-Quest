package org.arcadia.arc_quest.client.hud.quest.journal.detail;

/** Keeps the modal input barrier alive until its last exit frame has disappeared. */
public final class CollectionDetailTransition {
    public static final float ENTER_SECONDS = .20f;
    public static final float EXIT_SECONDS = .16f;
    private float progress;

    public void advance(boolean open, float seconds) {
        float elapsed = Float.isFinite(seconds) ? Math.max(0, seconds) : 0;
        progress = open ? Math.min(1, progress + elapsed / ENTER_SECONDS)
                : Math.max(0, progress - elapsed / EXIT_SECONDS);
    }

    public boolean visible(boolean open) { return open || progress > 0; }
    public boolean interactive(boolean open) { return open && progress >= 1; }
    public float alpha() { return progress * progress * (3 - 2 * progress); }
    public int offsetY() { return Math.round((1 - alpha()) * 8); }
    public static boolean shouldDraw(int alpha) { return alpha > 3; }
    public void reset() { progress = 0; }
}
