package org.arcadia.arc_quest.client.quest.collection;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtlasPageLayoutTest {
    @Test void exchangeResultHeadingQuantityAndIconStayOnTheSamePage() {
        var pages = AtlasPageLayout.paginate(List.of(new AtlasPageLayout.Block<>(23, "cost"),
                new AtlasPageLayout.Block<>(8, "result heading", 2),
                new AtlasPageLayout.Block<>(8, "quantity"), new AtlasPageLayout.Block<>(18, "actual item")), 40);
        assertEquals(List.of("cost"), pages.get(0).stream().map(AtlasPageLayout.Slice::content).toList());
        assertEquals(List.of("result heading", "quantity", "actual item"),
                pages.get(1).stream().map(AtlasPageLayout.Slice::content).toList());
    }

    @Test void oversizedSemanticGroupsFallBackToContinuationWithoutLosingContent() {
        var blocks = List.of(new AtlasPageLayout.Block<>(7, "previous"), new AtlasPageLayout.Block<>(8, "heading", 2),
                new AtlasPageLayout.Block<>(35, "native component"), new AtlasPageLayout.Block<>(8, "tail"));
        for (int height = 1; height <= 60; height++) {
            var slices = AtlasPageLayout.paginate(blocks, height).stream().flatMap(List::stream).toList();
            assertEquals(58, slices.stream().mapToInt(AtlasPageLayout.Slice::height).sum());
            for (var slice : slices) assertTrue(slice.y() >= 0 && slice.y() + slice.height() <= height);
        }
    }
    @Test void keepsNormalRowsWholeAndSplitsOnlyOversizedComponents() {
        var pages = AtlasPageLayout.paginate(List.of(new AtlasPageLayout.Block<>(18, "a"),
                new AtlasPageLayout.Block<>(16, "b"), new AtlasPageLayout.Block<>(75, "image")), 30);
        assertEquals(5, pages.size());
        assertEquals(0, pages.get(1).get(0).offset());
        assertEquals(List.of(0, 30, 60), pages.subList(2, 5).stream().map(p -> p.get(0).offset()).toList());
        assertEquals(109, pages.stream().flatMap(List::stream).mapToInt(AtlasPageLayout.Slice::height).sum());
    }

    @Test void repaginationAtEveryViewportHeightPreservesEveryPixelInOrder() {
        var blocks = List.of(new AtlasPageLayout.Block<>(12, "text"), new AtlasPageLayout.Block<>(245, "mod image"),
                new AtlasPageLayout.Block<>(33, "cost"), new AtlasPageLayout.Block<>(1, "tail"));
        for (int height = 1; height <= 320; height++) {
            var pages = AtlasPageLayout.paginate(blocks, height);
            var flattened = pages.stream().flatMap(List::stream).toList();
            assertEquals(291, flattened.stream().mapToInt(AtlasPageLayout.Slice::height).sum());
            for (var block : blocks) {
                int offset = 0;
                for (var slice : flattened.stream().filter(s -> s.content().equals(block.content())).toList()) {
                    assertEquals(offset, slice.offset()); offset += slice.height();
                    assertTrue(slice.y() >= 0 && slice.y() + slice.height() <= height);
                }
                assertEquals(block.height(), offset);
            }
        }
    }

    @Test void emptyDetailsStillHaveOnePageAndInvalidBoundsAreRejected() {
        assertEquals(1, AtlasPageLayout.paginate(List.of(), 10).size());
        assertThrows(IllegalArgumentException.class, () -> AtlasPageLayout.paginate(List.of(), 0));
        assertThrows(IllegalArgumentException.class, () -> new AtlasPageLayout.Block<>(0, "invalid"));
    }
}
