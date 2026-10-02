package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import java.util.Objects;

/** Short deliberate dwell prevents accidental tracking while browsing specimen cards. */
public final class CollectionTrackDwell {
    public static final float SECONDS = .20f;
    private String binding;
    private float elapsed;

    public void update(String hoveredBinding, float seconds) {
        if (!Objects.equals(binding, hoveredBinding)) { binding = hoveredBinding; elapsed = 0; }
        else if (binding != null && Float.isFinite(seconds)) elapsed += Math.max(0, seconds);
    }

    public boolean ready(String candidate) { return candidate != null && candidate.equals(binding) && elapsed >= SECONDS; }
    public void reset() { binding = null; elapsed = 0; }
}
