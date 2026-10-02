package org.arcadia.arc_quest.quest.api;

import java.util.Locale;

/** Explicit COLLECT meaning. Legacy acquisition remains the default for existing definitions. */
public enum CollectMode {
    /** Historical pickup / positive inventory delta accounting; this is not provenance tracking. */
    LEGACY_ACQUISITION,
    /** The quantity currently held in the main inventory and on the cursor; never accumulates pickups. */
    POSSESSION,
    /** Only output reported by the crafting event; smelting and mod machines are not included. */
    CRAFTED_ONLY;

    public static CollectMode from(ObjectiveEntry objective) {
        return parse(objective == null ? null : objective.getExtra("collect_mode"));
    }

    public static CollectMode parse(String value) {
        if (value == null || value.isBlank()) return LEGACY_ACQUISITION;
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public boolean acceptsAcquisition(boolean crafted) {
        return this == LEGACY_ACQUISITION || this == CRAFTED_ONLY && crafted;
    }
}
