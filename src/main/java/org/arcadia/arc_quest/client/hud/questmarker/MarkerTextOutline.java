package org.arcadia.arc_quest.client.hud.questmarker;

import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Cache the outline separately so embedded name colors cannot override its black stroke. */
final class MarkerTextOutline {
    private MarkerTextOutline() {}

    static FormattedCharSequence black(FormattedCharSequence text) {
        var glyphs = new ArrayList<FormattedCharSequence>();
        Map<Style, Style> styles = new HashMap<>();
        text.accept((index, style, codePoint) -> {
            glyphs.add(FormattedCharSequence.codepoint(codePoint,
                    styles.computeIfAbsent(style, original -> original.withColor(0))));
            return true;
        });
        return FormattedCharSequence.composite(glyphs);
    }
}
