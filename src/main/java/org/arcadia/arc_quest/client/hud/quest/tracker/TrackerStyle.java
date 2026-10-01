package org.arcadia.arc_quest.client.hud.quest.tracker;

/** Client presentation only; selecting a style never changes tracking or quest progress. */
public enum TrackerStyle {
    CLASSIC("classic"),
    FOCUS("focus"),
    OVERVIEW("overview");

    private final String key;

    TrackerStyle(String key) { this.key = key; }

    public String translationKey() { return "gui.arc_quest.tracker_style." + key; }
    public String descriptionKey() { return translationKey() + ".description"; }
    public TrackerStyle next() { return values()[(ordinal() + 1) % values().length]; }
}
