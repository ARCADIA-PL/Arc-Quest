package org.arcadia.arc_quest.client.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public final class StyledTextUtil {

    private StyledTextUtil() {
    }

    public static FormattedCharSequence fitSingleLine(Font font, Component text, int maxWidth) {
        Component safeText = text == null ? Component.empty() : text;
        if (maxWidth <= 0) return Component.empty().getVisualOrderText();
        if (font.width(safeText) <= maxWidth) return safeText.getVisualOrderText();

        List<FormattedCharSequence> lines = font.split(safeText, maxWidth);
        return lines.isEmpty() ? Component.empty().getVisualOrderText() : lines.get(0);
    }
}
