package org.arcadia.arc_quest.client.quest.collection;

import java.util.ArrayList;
import java.util.List;

/** Semantic keep rules for native tooltip lines and images, independent of Minecraft rendering. */
public final class AtlasTooltipLayout {
    private AtlasTooltipLayout() {}
    public record Part<T>(int height, String text, boolean textComponent, T content) {
        public boolean blank() { return textComponent && text.isBlank(); }
    }

    public static boolean heading(String text) {
        String line = text.strip();
        return line.endsWith(":") || line.endsWith("：")
                || line.equalsIgnoreCase("One Handed") || line.equalsIgnoreCase("Two Handed")
                || line.equals("单手持握") || line.equals("双手持握")
                || line.equals("单手") || line.equals("双手");
    }

    public static <T> List<AtlasPageLayout.Block<Part<T>>> blocks(List<Part<T>> source) {
        List<Part<T>> parts = new ArrayList<>();
        for (var part : source) {
            if (part.blank() && (parts.isEmpty() || parts.get(parts.size() - 1).blank())) continue;
            parts.add(part);
        }
        while (!parts.isEmpty() && parts.get(parts.size() - 1).blank()) parts.remove(parts.size() - 1);
        List<AtlasPageLayout.Block<Part<T>>> result = new ArrayList<>();
        for (var part : parts) result.add(new AtlasPageLayout.Block<>(part.blank() ? 3 : Math.max(1, part.height()), part));
        for (int start = 0; start < parts.size();) {
            if (parts.get(start).blank()) { start++; continue; }
            int end = start + 1;
            while (end < parts.size() && !parts.get(end).blank() && !heading(parts.get(end).text())) end++;
            // An ordinary paragraph or an attribute heading and its rows move together when they fit.
            var first = result.get(start);
            result.set(start, new AtlasPageLayout.Block<>(first.height(), first.content(), end - start - 1));
            // Oversized sections can continue, but never leave a lone final line on another page.
            if (end - start > 2) {
                var penultimate = result.get(end - 2);
                result.set(end - 2, new AtlasPageLayout.Block<>(penultimate.height(), penultimate.content(), 1));
            }
            start = end;
        }
        return List.copyOf(result);
    }

    /** A small reduction only for near fits. Long documents retain normal-sized text and paginate. */
    public static float fitScale(int height, int availableHeight) {
        return height > availableHeight && height <= availableHeight / .94
                ? Math.max(.94f, availableHeight / (float) height) : 1f;
    }
}
