package org.arcadia.arc_quest.client.hud.component;

import net.minecraft.client.gui.Font;

public final class HudTextUtil {
    private static final String ELLIPSIS = "…";

    private HudTextUtil() {
    }

    public static String ellipsize(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        int ellipsisWidth = font.width(ELLIPSIS);
        if (ellipsisWidth >= maxWidth) return "";
        return font.plainSubstrByWidth(text, maxWidth - ellipsisWidth) + ELLIPSIS;
    }
}
