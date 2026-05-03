package org.arcadia.arc_quest.mutil.theme;

public class ArcTheme {
    private int panelBackground = ArcColorPalette.PANEL_BG;
    private int panelBackgroundSoft = ArcColorPalette.PANEL_BG_SOFT;
    private int textPrimary = ArcColorPalette.TEXT_PRIMARY;
    private int textSecondary = ArcColorPalette.TEXT_SECONDARY;
    private int textMuted = ArcColorPalette.TEXT_MUTED;
    private int accentColor = ArcColorPalette.ACCENT_CYAN;
    private int outlineColor = ArcColorPalette.OUTLINE;
    private int shadowColor = ArcColorPalette.SHADOW;

    public static ArcTheme cyber() {
        return new ArcTheme();
    }

    public int panelBackground() {
        return panelBackground;
    }

    public ArcTheme panelBackground(int color) {
        this.panelBackground = color;
        return this;
    }

    public int panelBackgroundSoft() {
        return panelBackgroundSoft;
    }

    public ArcTheme panelBackgroundSoft(int color) {
        this.panelBackgroundSoft = color;
        return this;
    }

    public int textPrimary() {
        return textPrimary;
    }

    public ArcTheme textPrimary(int color) {
        this.textPrimary = color;
        return this;
    }

    public int textSecondary() {
        return textSecondary;
    }

    public ArcTheme textSecondary(int color) {
        this.textSecondary = color;
        return this;
    }

    public int textMuted() {
        return textMuted;
    }

    public ArcTheme textMuted(int color) {
        this.textMuted = color;
        return this;
    }

    public int accentColor() {
        return accentColor;
    }

    public ArcTheme accentColor(int color) {
        this.accentColor = color;
        return this;
    }

    public int outlineColor() {
        return outlineColor;
    }

    public ArcTheme outlineColor(int color) {
        this.outlineColor = color;
        return this;
    }

    public int shadowColor() {
        return shadowColor;
    }

    public ArcTheme shadowColor(int color) {
        this.shadowColor = color;
        return this;
    }
}
