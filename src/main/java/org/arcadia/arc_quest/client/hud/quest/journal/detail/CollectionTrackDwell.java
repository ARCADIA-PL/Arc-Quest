package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.Map;

/** Short deliberate dwell prevents accidental tracking while browsing specimen cards. */
public final class CollectionTrackDwell {
    public static final float SECONDS = .20f;
    public static final float SWITCH_SECONDS = .12f;
    private String binding;
    private float elapsed;
    private final Map<String, Float> appearances = new LinkedHashMap<>();

    public void update(String hoveredBinding, float seconds) {
        float dt = Float.isFinite(seconds) ? Math.max(0, seconds) : 0;
        if (!Objects.equals(binding, hoveredBinding)) { binding = hoveredBinding; elapsed = 0; }
        else if (binding != null) elapsed += dt;
        if (ready(binding)) appearances.putIfAbsent(binding, 0f);
        var iterator = appearances.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            float value = entry.getValue();
            value = ready(entry.getKey()) ? Math.min(1, value + dt / SWITCH_SECONDS)
                    : Math.max(0, value - dt / SWITCH_SECONDS);
            if (value == 0 && !ready(entry.getKey())) iterator.remove();
            else entry.setValue(value);
        }
    }

    public boolean ready(String candidate) { return candidate != null && candidate.equals(binding) && elapsed >= SECONDS; }
    public float appearance(String candidate) { return appearances.getOrDefault(candidate, 0f); }
    public void reset() { binding = null; elapsed = 0; appearances.clear(); }
}
