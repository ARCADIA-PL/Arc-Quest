package org.arcadia.arc_quest.client.hud.questmarker;

import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarkerTextOutlineTest {
    private record Glyph(int codePoint, Style style) {}

    private static List<Glyph> glyphs(FormattedCharSequence sequence) {
        var result = new ArrayList<Glyph>();
        sequence.accept((index, style, cp) -> { result.add(new Glyph(cp, style)); return true; });
        return result;
    }

    @Test void legacyBossColorsCannotRecolorTheOutline() {
        FormattedCharSequence source = sink -> StringDecomposer.iterateFormatted(
                "§6凝视深渊的不屈命运之英魂§cBOSS§r", Style.EMPTY, sink);
        var foreground = glyphs(source);
        var outline = glyphs(MarkerTextOutline.black(source));
        assertEquals(foreground.stream().map(Glyph::codePoint).toList(), outline.stream().map(Glyph::codePoint).toList());
        assertTrue(foreground.stream().anyMatch(g -> g.style().getColor().getValue() == 0xFFAA00));
        assertTrue(outline.stream().allMatch(g -> g.style().getColor().getValue() == 0));
        assertEquals(foreground, glyphs(source), "Preparing the outline mutated the foreground");
    }

    @Test void styledAndSupplementaryGlyphsKeepTheirGeometryAndFont() {
        var style = Style.EMPTY.withColor(0xF0A030).withBold(true).withItalic(true)
                .withFont(ResourceLocation.parse("example:boss_font"));
        var source = FormattedCharSequence.forward("英魂𠀀", style);
        var outline = glyphs(MarkerTextOutline.black(source));
        assertEquals(List.of((int)'英', (int)'魂', 0x20000), outline.stream().map(Glyph::codePoint).toList());
        assertTrue(outline.stream().allMatch(g -> g.style().equals(style.withColor(0))));
    }

    @Test void emptyAndShortCircuitingSequencesRemainValid() {
        assertTrue(MarkerTextOutline.black(FormattedCharSequence.EMPTY).accept((i,s,c) -> false));
        var seen = new ArrayList<Integer>();
        assertFalse(MarkerTextOutline.black(FormattedCharSequence.forward("ABC", Style.EMPTY))
                .accept((i,s,c) -> { seen.add(c); return false; }));
        assertEquals(List.of((int)'A'), seen);
    }
}
