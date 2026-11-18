package org.arcadia.arc_quest.client.hud.style;

import org.arcadia.arc_quest.config.HudArtStyle;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class HudStylePaletteTest {
    private static final int[] THEMES = {0x56C8FF, 0xFFD166, 0xB76EFF, 0xFF5555, 0x00FF00, 0x0000FF, 0xFFFFFF, 0};

    @Test void settingCycleReachesEveryStyleAndReturnsToTheOriginalDefault() {
        var reached = EnumSet.noneOf(HudArtStyle.class);
        var keys = new HashSet<String>();
        HudArtStyle style = HudArtStyle.DEFAULT;
        do {
            assertTrue(reached.add(style));
            assertTrue(keys.add(style.translationKey()));
            assertEquals(style, HudArtStyle.valueOf(style.name()));
            style = style.next();
        } while (style != HudArtStyle.DEFAULT);
        assertEquals(EnumSet.allOf(HudArtStyle.class), reached);
        assertEquals(HudArtStyle.ORE, HudArtStyle.DEFAULT.next());
    }
    @Test void glassKeepsOriginalOpacityThroughoutEveryEntranceAndExitFade() {
        for (int theme : THEMES) for (int alpha = 0; alpha < 256; alpha++) {
            int original = alpha << 24 | 0x10151B;
            assertEquals(alpha, HudStylePalette.glass(original, theme) >>> 24);
            if (theme == 0 || theme == 0xFFFFFF) assertEquals(original, HudStylePalette.glass(original, theme));
        }
    }
    @Test void invalidOpacityAndProgressValuesCannotFlashVisibleGeometry() {
        for (float invalid : new float[]{-1, Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY}) {
            assertEquals(0, HudStylePalette.argb(0x56C8FF, invalid) >>> 24);
            assertEquals(0, HudStylePalette.unit(invalid));
        }
        assertEquals(255, HudStylePalette.argb(0x56C8FF, 2) >>> 24);
        assertEquals(128, HudStylePalette.argb(0x56C8FF, .5f) >>> 24);
    }
    @Test void materialAndActionLabelsStayReadableAcrossThemesAndInteractionStates() {
        for (HudArtStyle style : new HudArtStyle[]{HudArtStyle.MODERNA, HudArtStyle.CYBERPUNK, HudArtStyle.WYRMHEART}) {
            for (int theme : THEMES) for (boolean hover : new boolean[]{false, true}) for (boolean selected : new boolean[]{false, true}) {
                assertTrue(contrast(0xFFFFFF, HudStylePalette.surface(style, theme, selected, hover)) >= 4.5);
                assertTrue(contrast(0xFFFFFF, HudStylePalette.actionFace(theme, hover, selected)) >= 4.5);
                assertTrue(contrast(0xFFFFFF, HudStylePalette.field(style, theme)) >= 7);
            }
            assertNotEquals(HudStylePalette.surface(style, 0xFF5555, false, false), HudStylePalette.surface(style, 0x56C8FF, false, false));
            assertNotEquals(HudStylePalette.field(style, 0xFF5555), HudStylePalette.field(style, 0x56C8FF));
        }
        assertEquals(0, HudStylePalette.actionFace(0xFF0000, true, false) & 0xFFFF);
        assertEquals(0, HudStylePalette.actionFace(0x0000FF, true, false) & 0xFFFF00);
    }
    private static double contrast(int a, int b) {
        double x = luminance(a), y = luminance(b);
        return (Math.max(x, y) + .05) / (Math.min(x, y) + .05);
    }
    private static double luminance(int rgb) {
        return channel(rgb >> 16 & 255) * .2126 + channel(rgb >> 8 & 255) * .7152 + channel(rgb & 255) * .0722;
    }
    private static double channel(int value) {
        double s = value / 255d;
        return s <= .04045 ? s / 12.92 : Math.pow((s + .055) / 1.055, 2.4);
    }
}
