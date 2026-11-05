package org.arcadia.arc_quest.client.quest.collection;

import java.util.ArrayList;
import java.util.List;

/** Height-only pagination, independent of fonts, game state and render/input APIs. */
public final class AtlasPageLayout {
    private AtlasPageLayout() {}
    public record Block<T>(int height, T content, int keepWithNext) {
        public Block(int height, T content) { this(height, content, 0); }
        public Block {
            if (height < 1) throw new IllegalArgumentException("Nonpositive block height");
            if (keepWithNext < 0) throw new IllegalArgumentException("Negative keep-with-next count");
        }
    }
    /** Oversized native tooltip components continue at offset on the next page, without losing pixels. */
    public record Slice<T>(int y, int offset, int height, int totalHeight, T content) {}
    public static <T> List<List<Slice<T>>> paginate(List<Block<T>> blocks, int availableHeight) {
        if (availableHeight < 1) throw new IllegalArgumentException("Nonpositive page height");
        List<List<Slice<T>>> pages = new ArrayList<>();
        List<Slice<T>> page = new ArrayList<>();
        int y = 0;
        for (int i = 0; i < blocks.size(); i++) {
            var block = blocks.get(i);
            long groupHeight = block.height();
            for (int j = i + 1; j < blocks.size() && j - i <= block.keepWithNext(); j++)
                groupHeight += blocks.get(j).height();
            // Keep a semantic row/section whole when it fits a page; oversized content still continues.
            if (y > 0 && (block.height() > availableHeight - y
                    || groupHeight <= availableHeight && groupHeight > availableHeight - y)) {
                pages.add(List.copyOf(page)); page.clear(); y = 0;
            }
            for (int offset = 0; offset < block.height();) {
                int take = Math.min(block.height() - offset, availableHeight - y);
                page.add(new Slice<>(y, offset, take, block.height(), block.content()));
                y += take; offset += take;
                if (y == availableHeight) {
                    pages.add(List.copyOf(page)); page.clear(); y = 0;
                }
            }
        }
        if (!page.isEmpty() || pages.isEmpty()) pages.add(List.copyOf(page));
        return List.copyOf(pages);
    }
}
