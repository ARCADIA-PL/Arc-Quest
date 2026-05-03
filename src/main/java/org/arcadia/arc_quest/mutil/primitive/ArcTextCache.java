package org.arcadia.arc_quest.mutil.primitive;

import net.minecraft.client.gui.Font;

public class ArcTextCache {
    private String source = "";
    private String clipped = "";
    private int maxWidth = -1;
    private int width = -1;

    public String clip(Font font, String text, int maxWidth) {
        String next = text == null ? "" : text;
        if (!next.equals(source) || this.maxWidth != maxWidth) {
            this.source = next;
            this.maxWidth = maxWidth;
            this.clipped = font.plainSubstrByWidth(next, maxWidth);
            this.width = font.width(clipped);
        }
        return clipped;
    }

    public int width(Font font, String text) {
        String next = text == null ? "" : text;
        if (!next.equals(source) || width < 0) {
            this.source = next;
            this.clipped = next;
            this.width = font.width(next);
            this.maxWidth = -1;
        }
        return width;
    }

    public void invalidate() {
        source = "";
        clipped = "";
        maxWidth = -1;
        width = -1;
    }
}
