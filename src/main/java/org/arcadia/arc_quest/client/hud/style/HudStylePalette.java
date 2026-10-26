package org.arcadia.arc_quest.client.hud.style;

import org.arcadia.arc_quest.config.HudArtStyle;

/** Native material colors; caller-owned theme, rarity and status colors stay authoritative. */
public final class HudStylePalette {
    private HudStylePalette() {}

    public static float unit(float value) {
        return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
    }
    public static int argb(int rgb, float alpha) {
        return Math.round(255 * unit(alpha)) << 24 | rgb & 0xFFFFFF;
    }
    public static int mix(int base, int tint, float amount) {
        float t = unit(amount);
        int r = Math.round((base >> 16 & 255) * (1 - t) + (tint >> 16 & 255) * t);
        int g = Math.round((base >> 8 & 255) * (1 - t) + (tint >> 8 & 255) * t);
        int b = Math.round((base & 255) * (1 - t) + (tint & 255) * t);
        return r << 16 | g << 8 | b;
    }
    public static int tint(int base, int theme, float amount) {
        int max = Math.max(theme >> 16 & 255, Math.max(theme >> 8 & 255, theme & 255));
        int min = Math.min(theme >> 16 & 255, Math.min(theme >> 8 & 255, theme & 255));
        return max - min > 10 ? mix(base, theme, amount) : base & 0xFFFFFF;
    }
    public static int shade(int rgb, float brightness) { return mix(0, rgb, brightness); }
    public static int glass(int background, int theme) {
        return background & 0xFF000000 | tint(background, theme, .10f);
    }
    public static int surface(HudArtStyle style, int theme, boolean selected, boolean hovered) {
        int base = switch (style) {
            case MODERNA -> 0x231F1B;
            case WYRMHEART -> 0x22221F;
            default -> 0x10151F;
        };
        return tint(base, theme,
                selected ? .30f : hovered ? .23f : .14f);
    }
    public static int field(HudArtStyle style, int theme) {
        int base = switch (style) {
            case MODERNA -> 0x12100F;
            case WYRMHEART -> 0x11120F;
            default -> 0x090D14;
        };
        return tint(base, theme, .10f);
    }
    public static int actionFace(int theme, boolean hovered, boolean pressed) {
        int rgb = shade(theme, pressed ? .32f : hovered ? .56f : .46f);
        // White labels must remain readable even for yellow and white themes.
        while (luminance(rgb) > .18) rgb = shade(rgb, .94f);
        return rgb;
    }
    private static double luminance(int rgb) {
        return channel(rgb >> 16 & 255) * .2126 + channel(rgb >> 8 & 255) * .7152 + channel(rgb & 255) * .0722;
    }
    private static double channel(int value) {
        double s = value / 255d;
        return s <= .04045 ? s / 12.92 : Math.pow((s + .055) / 1.055, 2.4);
    }
}
