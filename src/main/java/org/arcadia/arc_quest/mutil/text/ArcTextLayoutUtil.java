package org.arcadia.arc_quest.mutil.text;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public final class ArcTextLayoutUtil {
    private ArcTextLayoutUtil() {
    }

    public static List<String> wrapPlain(Font font, String text, int maxWidth) {
        List<String> result = new ArrayList<>();
        if (font == null || text == null || text.isEmpty()) return result;
        int safeWidth = Math.max(1, maxWidth);
        for (String paragraph : text.split("\\n", -1)) {
            if (paragraph.trim().isEmpty()) {
                result.add("");
                continue;
            }
            String remaining = paragraph;
            while (!remaining.isEmpty()) {
                String clipped = font.plainSubstrByWidth(remaining, safeWidth);
                if (clipped.isEmpty()) {
                    result.add(remaining.substring(0, 1));
                    remaining = remaining.substring(1);
                    continue;
                }
                int end = clipped.length();
                if (end < remaining.length()) {
                    int lastSpace = clipped.lastIndexOf(' ');
                    if (lastSpace > 0) end = lastSpace;
                }
                String line = remaining.substring(0, end).trim();
                if (!line.isEmpty()) result.add(line);
                remaining = remaining.substring(Math.min(remaining.length(), end)).trim();
            }
        }
        return result;
    }

    public static List<Page> paginateStory(Font font, String title, Component description, Component story, int maxWidth, int maxPageHeight, int leftPad, int themeColor) {
        List<Page> pages = new ArrayList<>();
        PageBuilder builder = new PageBuilder(font, maxPageHeight, pages);
        builder.addWrapped(title, Math.round(maxWidth / 1.35f), leftPad, themeColor, 1.35f, 6);
        builder.addSpacing(12);
        if (description != null && !description.getString().isEmpty()) {
            builder.addParagraphs(description.getString(), Math.round(maxWidth / 0.95f), leftPad, 0x99AABB, 0.95f, 5, Math.round(font.lineHeight * 0.95f));
            builder.addSpacing(16);
        }
        if (story != null && !story.getString().isEmpty()) {
            builder.addParagraphs(story.getString(), maxWidth, leftPad, 0xE0E0E0, 1.0f, 6, font.lineHeight);
        }
        builder.finish();
        if (pages.isEmpty()) pages.add(new Page(List.of()));
        return pages;
    }

    public static TooltipText tooltipText(Font font, Component title, Component description, int descWidth) {
        Component safeTitle = title == null ? Component.empty() : title;
        List<FormattedCharSequence> descLines = description == null ? List.of() : font.split(description, Math.max(1, descWidth));
        int textMaxWidth = font.width(safeTitle);
        for (FormattedCharSequence line : descLines) {
            textMaxWidth = Math.max(textMaxWidth, font.width(line));
        }
        return new TooltipText(safeTitle, font.width(safeTitle), descLines, textMaxWidth);
    }

    public static List<CollectionRow> collectionRows(Font font, Iterable<String> phaseIds, PhaseLookup lookup) {
        List<CollectionRow> rows = new ArrayList<>();
        if (phaseIds == null || lookup == null) return rows;
        for (String phaseId : phaseIds) {
            CollectionRow row = lookup.rowFor(phaseId);
            if (row != null) rows.add(row.withWidth(font.width(row.text())));
        }
        return rows;
    }

    private static final class PageBuilder {
        private final Font font;
        private final int maxPageHeight;
        private final List<Page> pages;
        private final List<StyledLine> lines = new ArrayList<>();
        private int currentYSpace;

        private PageBuilder(Font font, int maxPageHeight, List<Page> pages) {
            this.font = font;
            this.maxPageHeight = Math.max(1, maxPageHeight);
            this.pages = pages;
        }

        private void addWrapped(String text, int width, int x, int color, float scale, int extraLineSpacing) {
            for (String line : wrapPlain(font, text, width)) {
                if (line.isEmpty()) {
                    addSpacing(Math.round(font.lineHeight * scale));
                    continue;
                }
                int lineHeight = Math.round((float) Math.ceil(font.lineHeight * scale)) + extraLineSpacing;
                ensureSpace(lineHeight);
                lines.add(new StyledLine(line, x, color, scale, lineHeight));
                currentYSpace += lineHeight;
            }
        }

        private void addParagraphs(String text, int width, int x, int color, float scale, int extraLineSpacing, int blankLineHeight) {
            for (String paragraph : text.split("\\n", -1)) {
                if (paragraph.trim().isEmpty()) {
                    addSpacing(blankLineHeight);
                    continue;
                }
                addWrapped(paragraph, width, x, color, scale, extraLineSpacing);
            }
        }

        private void addSpacing(int height) {
            if (height <= 0) return;
            ensureSpace(height);
            currentYSpace += height;
        }

        private void ensureSpace(int height) {
            if (currentYSpace + height > maxPageHeight && currentYSpace > 0) {
                pages.add(new Page(List.copyOf(lines)));
                lines.clear();
                currentYSpace = 0;
            }
        }

        private void finish() {
            if (!lines.isEmpty() || pages.isEmpty()) pages.add(new Page(List.copyOf(lines)));
        }
    }

    public record StyledLine(String text, int x, int color, float scale, int lineHeight) {
    }

    public record Page(List<StyledLine> lines) {
    }

    public record TooltipText(Component title, int titleWidth, List<FormattedCharSequence> descLines, int textMaxWidth) {
    }

    public record CollectionRow(String text, int color, int width) {
        public CollectionRow(String text, int color) {
            this(text, color, 0);
        }

        CollectionRow withWidth(int width) {
            return new CollectionRow(text, color, width);
        }
    }

    public interface PhaseLookup {
        CollectionRow rowFor(String phaseId);
    }
}
