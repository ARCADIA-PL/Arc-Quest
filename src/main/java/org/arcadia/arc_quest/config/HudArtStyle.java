package org.arcadia.arc_quest.config;

/** Client presentation only; independent from tracker layout and server content. */
public enum HudArtStyle {
    DEFAULT, ORE, MODERNA, CYBERPUNK, WYRMHEART;

    public HudArtStyle next() { return switch (this) {
        case DEFAULT -> ORE;
        case ORE -> MODERNA;
        case MODERNA -> CYBERPUNK;
        case CYBERPUNK -> WYRMHEART;
        case WYRMHEART -> DEFAULT;
    }; }
    public String translationKey() { return "gui.arc_quest.hud_style." + name().toLowerCase(java.util.Locale.ROOT); }
}
