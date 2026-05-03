package org.arcadia.arc_quest.mutil.text;

import net.minecraft.client.gui.Font;

import java.util.ArrayList;
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
            this.lines = wrapText(safeText, safeWidth, font);
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

    private static List<String> wrapText(String text, int maxWidth, Font font) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) return result;
        String[] paragraphs = text.split("\\n");
        for (String paragraph : paragraphs) {
            String[] words = paragraph.split(" ");
            StringBuilder current = new StringBuilder();
            for (String word : words) {
                String test = current.isEmpty() ? word : current + " " + word;
                if (font.width(test) > maxWidth && !current.isEmpty()) {
                    result.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    if (!current.isEmpty()) current.append(' ');
                    current.append(word);
                }
            }
            if (!current.isEmpty()) result.add(current.toString());
        }
        return result;
    }
}
