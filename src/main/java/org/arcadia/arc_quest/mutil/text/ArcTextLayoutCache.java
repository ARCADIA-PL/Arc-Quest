package org.arcadia.arc_quest.mutil.text;

import net.minecraft.client.gui.Font;

import java.util.List;

public final class ArcTextLayoutCache {
    private String text = "";
    private int width = -1;
    private int lineHeight = -1;
    private List<String> lines = List.of();
    private int height;

    public List<String> layout(Font font, String text, int width) {
        String safeText = text == null ? "" : text;
        int safeWidth = Math.max(1, width);
        if (!safeText.equals(this.text) || this.width != safeWidth || this.lineHeight != font.lineHeight) {
            this.text = safeText;
            this.width = safeWidth;
            this.lineHeight = font.lineHeight;
            this.lines = ArcTextLayoutUtil.wrapPlain(font, safeText, safeWidth);
            this.height = lines.isEmpty() ? 0 : lines.size() * font.lineHeight;
        }
        return lines;
    }

    public int height(Font font, String text, int width) {
        layout(font, text, width);
        return height;
    }

    public int getHeight() {
        return height;
    }

    public void invalidate() {
        text = "";
        width = -1;
        lineHeight = -1;
        lines = List.of();
        height = 0;
    }
}
