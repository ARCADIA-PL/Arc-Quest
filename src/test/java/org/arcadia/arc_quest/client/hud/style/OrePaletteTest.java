package org.arcadia.arc_quest.client.hud.style;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrePaletteTest {
    @Test void keepsEveryFadeStepAndStatusDistinctions() {
        for (int alpha = 0; alpha < 256; alpha++) {
            for (int rgb : new int[]{0, 0x101720, 0xFFFFFF, 0x667788, 0x33FF66, 0xFF3333, 0xFFD700}) {
                int color = (alpha << 24) | rgb;
                assertEquals(alpha, OrePalette.decoration(color) >>> 24);
                assertEquals(alpha, OrePalette.text(color) >>> 24);
            }
        }
        assertNotEquals(OrePalette.decoration(0xFFFF3333), OrePalette.decoration(0xFF33FF66));
        assertNotEquals(OrePalette.decoration(0xFFFFD700), OrePalette.decoration(0xFF33FF66));
        assertEquals(0xFF000000 | OrePalette.BORDER, OrePalette.text(0xFF000000 | OrePalette.BORDER));
    }
    @Test void preservesArcqThemeStatusAndRarityColorsInsteadOfMakingThemAllGreen() {
        for (int rgb : new int[]{0x56C8FF, 0xFFD700, 0x85C6AE, 0xAA55FF, 0xFF3333, 0x55FF55, 0x4488CC}) {
            for (int alpha : new int[]{0, 1, 64, 128, 255}) {
                int color = (alpha << 24) | rgb;
                assertEquals(color, OrePalette.decoration(color));
                assertEquals(color, OrePalette.text(color));
            }
            for (float state : new float[]{.48f, .60f, .72f})
                assertTrue(contrast(0xFFFFFF, OrePalette.actionFace(rgb, state)) >= 4.5);
        }
        assertEquals(0, OrePalette.actionFace(0xFF0000, .60f) & 0x00FFFF);
        assertEquals(0, OrePalette.actionFace(0x0000FF, .60f) & 0xFFFF00);
        assertNotEquals(OrePalette.actionFace(0x56C8FF, .60f), OrePalette.actionFace(0xFFD700, .60f));
    }
    @Test void invalidOpacityCannotFlashAnOpaquePanel() {
        for (float opacity : new float[]{-1, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
            assertEquals(0, OrePalette.argb(0x56C8FF, opacity) >>> 24);
        assertEquals(255, OrePalette.argb(0x56C8FF, 2) >>> 24);
        assertEquals(128, OrePalette.argb(0x56C8FF, .5f) >>> 24);
    }
    @Test void glassRetainsOriginalOpacityForEveryThemeAndFadeStep() {
        for (int theme : new int[]{0xFFFFFF, 0x56C8FF, 0xFFD166, 0xB76EFF, 0xFF5555}) {
            for (int alpha = 0; alpha < 256; alpha++) {
                int background = alpha << 24 | 0x10151B;
                int glass = OrePalette.glassBackground(background, theme);
                assertEquals(alpha, glass >>> 24, "Theme tint must not replace or multiply background alpha");
                if (theme == 0xFFFFFF) assertEquals(background, glass);
            }
        }
    }
    @Test void neutralMaterialsCarryThemeHueWithoutRecoloringNeutralThemes() {
        int base = 0x303030;
        assertEquals(base, OrePalette.tintSurface(base, 0xFFFFFF, .4f));
        assertEquals(base, OrePalette.tintSurface(base, 0xFF5555, Float.NaN));
        assertEquals(base, OrePalette.tintSurface(base, 0xFF5555, -1));
        assertEquals(0xFF5555, OrePalette.tintSurface(base, 0xFF5555, 2));
        int red = OrePalette.tintSurface(base, 0xFF5555, .25f);
        int blue = OrePalette.tintSurface(base, 0x56C8FF, .25f);
        assertTrue((red >> 16 & 255) > (red & 255));
        assertTrue((blue & 255) > (blue >> 16 & 255));
        assertNotEquals(OrePalette.glassBackground(0x8010151B, 0xFF5555),
                OrePalette.glassBackground(0x8010151B, 0x56C8FF));
    }
    @Test void tintedControlsKeepTheirTextReadableAcrossThemesAndStates() {
        for (int theme : new int[]{0x56C8FF, 0xFFD166, 0xB76EFF, 0xFF5555, 0x0000FF, 0x00FF00}) {
            for (int base : new int[]{OrePalette.SECONDARY, OrePalette.SECONDARY_HOVER, OrePalette.SECONDARY_PRESSED}) {
                for (float amount : new float[]{.16f, .25f})
                    assertTrue(contrast(OrePalette.BORDER, OrePalette.tintSurface(base, theme, amount)) >= 4.5);
            }
            assertTrue(contrast(OrePalette.MUTED, OrePalette.tintSurface(OrePalette.RAISED, theme, .12f)) >= 4.5);
            assertTrue(contrast(OrePalette.TEXT, OrePalette.shade(theme, .30f)) >= 4.5);
        }
    }
    @Test void bodyAndSecondaryActionTextRemainReadable() {
        assertTrue(contrast(OrePalette.TEXT, OrePalette.RAISED) >= 7);
        assertTrue(contrast(OrePalette.BORDER, OrePalette.SECONDARY) >= 7);
        assertTrue(contrast(OrePalette.MUTED, OrePalette.SUNKEN) >= 7);
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
