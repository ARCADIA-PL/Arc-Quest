/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 * Palette adapted from katorlys/OreUI src/styles/tokens.css (MPL-2.0).
 */
package org.arcadia.arc_quest.client.hud.style;

/** Ore's neutral surfaces; all accent colors are supplied by ArcQ's existing theme owners. */
public final class OrePalette {
    public static final int BORDER = 0x1E1E1F;
    public static final int SURFACE = 0x48494A;
    public static final int RAISED = 0x313233;
    public static final int SUNKEN = 0x242425;
    public static final int SPECULAR = 0x6D6D6E;
    public static final int BEVEL_SHADOW = 0x323334;
    public static final int SECONDARY = 0xD0D1D4;
    public static final int SECONDARY_HOVER = 0xF4F6F9;
    public static final int SECONDARY_PRESSED = 0xB1B2B5;
    public static final int SECONDARY_SHADOW = 0x58585A;
    public static final int TEXT = 0xFFFFFF;
    public static final int MUTED = 0xD0D1D4;

    private OrePalette() {}

    public static int argb(int rgb, float opacity) {
        float safe = Float.isFinite(opacity) ? Math.max(0, Math.min(1, opacity)) : 0;
        return (Math.round(255 * safe) << 24) | (rgb & 0xFFFFFF);
    }

    /** Keep ArcQ theme/status/rarity hues; only neutral decoration enters the Ore surface palette. */
    public static int decoration(int argb) {
        int rgb = argb & 0xFFFFFF;
        int r = rgb >>> 16, g = (rgb >>> 8) & 255, b = rgb & 255;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        if (max - min > 10) return argb;
        int mapped = rgb == 0 ? 0 : max < 80 ? RAISED
                : max < 140 ? SECONDARY_SHADOW : max < 225 ? MUTED : TEXT;
        return (argb & 0xFF000000) | mapped;
    }

    /** Authored colors remain intact, including Component styles and dark button labels. */
    public static int text(int argb) {
        int rgb = argb & 0xFFFFFF;
        if (rgb == 0 || rgb == BORDER || rgb == SURFACE) return argb;
        int r = rgb >>> 16, g = (rgb >>> 8) & 255, b = rgb & 255;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        if (max - min > 10) return argb;
        return (argb & 0xFF000000) | (max < 200 ? MUTED : TEXT);
    }

    public static boolean isChromatic(int rgb) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > 10;
    }

    /** Light material tint; saturated content colors themselves are never remapped. */
    public static int tintSurface(int base, int theme, float amount) {
        if (!isChromatic(theme)) return base & 0xFFFFFF;
        float weight = Float.isFinite(amount) ? Math.max(0, Math.min(1, amount)) : 0;
        int r = Math.round((base >> 16 & 255) * (1 - weight) + (theme >> 16 & 255) * weight);
        int g = Math.round((base >> 8 & 255) * (1 - weight) + (theme >> 8 & 255) * weight);
        int b = Math.round((base & 255) * (1 - weight) + (theme & 255) * weight);
        return r << 16 | g << 8 | b;
    }

    /** Preserve the original dark-glass opacity, including every entrance/exit fade step. */
    public static int glassBackground(int background, int theme) {
        return (background & 0xFF000000) | tintSurface(background, theme, .10f);
    }

    /** Bevel lighting scales the supplied theme without substituting a different hue. */
    public static int shade(int theme, float factor) {
        return (Math.round((theme >> 16 & 255) * factor) << 16)
                | (Math.round((theme >> 8 & 255) * factor) << 8)
                | Math.round((theme & 255) * factor);
    }

    /** Darken bright yellow/cyan/white faces enough for the white action label. */
    public static int actionFace(int theme, float factor) {
        int rgb = shade(theme, factor);
        while (luminance(rgb) > .1834 && factor > .05f) {
            factor *= .94f;
            rgb = shade(theme, factor);
        }
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
