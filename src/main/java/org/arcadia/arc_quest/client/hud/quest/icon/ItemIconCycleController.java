package org.arcadia.arc_quest.client.hud.quest.icon;

import java.util.List;

/** Time-driven, per-objective state. No renderer, registry, or JEI dependency. */
public final class ItemIconCycleController {
    public static final long DEFAULT_INTERVAL_MS = 1000;
    private final long interval;
    private List<String> keys = List.of();
    private int index;
    private long previousTime = -1;
    private long elapsed;
    private boolean wasPaused;

    public ItemIconCycleController() { this(DEFAULT_INTERVAL_MS); }
    public ItemIconCycleController(long interval) {
        if (interval <= 0) throw new IllegalArgumentException("interval must be positive");
        this.interval = interval;
    }

    public int select(List<String> candidates, long now, boolean paused) {
        if (!keys.equals(candidates)) {
            String selected = keys.isEmpty() ? null : keys.get(index);
            keys = List.copyOf(candidates);
            index = selected == null ? 0 : Math.max(0, keys.indexOf(selected));
            elapsed = 0;
            previousTime = now;
        }
        long delta = previousTime < 0 ? 0 : Math.max(0, now - previousTime);
        previousTime = now;
        if (!paused && !wasPaused && keys.size() > 1) {
            elapsed += delta;
            index = (int) ((index + elapsed / interval) % keys.size());
            elapsed %= interval;
        }
        wasPaused = paused;
        return keys.isEmpty() ? -1 : index;
    }

    /** Preserve the displayed item while a screen is suspended or a row is hidden. */
    public void suspend() { previousTime = -1; wasPaused = true; }
}
