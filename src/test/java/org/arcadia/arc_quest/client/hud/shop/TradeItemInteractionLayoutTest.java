package org.arcadia.arc_quest.client.hud.shop;

import org.arcadia.arc_quest.client.compat.jei.screen.JeiHitBounds;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TradeItemInteractionLayoutTest {
    @Test void singleProductIncludesFourPixelPaddingOnEverySide() {
        for (int edge : new int[] {16, 20}) {
            var hit = TradeItemInteractionLayout.product(20, 30, edge);
            assertEquals(new JeiHitBounds(16, 26, 24 + edge, 34 + edge), hit);
            assertTrue(hit.contains(17, 31));
            assertTrue(hit.contains(21, 27));
            assertFalse(hit.contains(15, 31));
            assertFalse(hit.contains(24 + edge, 31));
        }
    }

    @Test void entireCostTokenCanBeSelectedWithoutClaimingPlusOrSibling() {
        var first = TradeItemInteractionLayout.cost(10, 40, 60, 10);
        var next = TradeItemInteractionLayout.cost(80, 40, 35, 10);
        assertEquals(new JeiHitBounds(10, 37, 70, 55), first);
        assertTrue(first.contains(65, 45)); // Item name, outside the ten-pixel icon.
        assertTrue(first.contains(15, 53)); // Below the old target.
        assertFalse(first.contains(75, 45)); // Plus sign / gap.
        assertFalse(next.contains(75, 45));
        assertTrue(first.intersect(next).empty());
    }

    @Test void expandedCompositeProductsRemainInsideCellsAndOutsidePageArrows() {
        for (int[] size : new int[][] {{30, 20}, {20, 30}, {38, 34}, {72, 18}}) {
            var paging = TradeIngredientSlotLayout.paging(size[0], size[1]);
            var body = paging.content();
            var cells = TradeIngredientSlotLayout.fit(2, body.width(), body.height(), 20);
            JeiHitBounds previous = null;
            for (var cell : cells) {
                double x = body.x() + cell.x(), y = body.y() + cell.y();
                var cellBounds = new JeiHitBounds(x, y, x + cell.width(), y + cell.height());
                var hit = TradeItemInteractionLayout.product(x + (cell.width() - cell.iconSize()) / 2,
                        y + (cell.height() - cell.iconSize()) / 2, cell.iconSize()).intersect(cellBounds);
                assertFalse(hit.empty());
                if (previous != null) assertTrue(previous.intersect(hit).empty());
                for (var arrow : List.of(paging.previous(), paging.next()))
                    assertTrue(hit.intersect(new JeiHitBounds(arrow.x(), arrow.y(),
                            arrow.x() + arrow.width(), arrow.y() + arrow.height())).empty());
                previous = hit;
            }
        }
    }

    @Test void costExpansionKeepsHorizontalPagingButtonsSeparate() {
        var paging = TradeIngredientSlotLayout.paging(72, 10, 24);
        var body = paging.content();
        var hit = TradeItemInteractionLayout.cost(body.x(), body.y(), body.width(), body.height());
        for (var arrow : List.of(paging.previous(), paging.next()))
            assertTrue(hit.intersect(new JeiHitBounds(arrow.x(), arrow.y(),
                    arrow.x() + arrow.width(), arrow.y() + arrow.height())).empty());
    }

    @Test void expandedHitStillObeysViewportClip() {
        var clip = new JeiHitBounds(0, 0, 100, 60);
        var hit = TradeItemInteractionLayout.product(90, 50, 20).intersect(clip);
        assertEquals(new JeiHitBounds(86, 46, 100, 60), hit);
        assertTrue(hit.contains(98, 58));
        assertFalse(hit.contains(100, 58));
        assertTrue(TradeItemInteractionLayout.product(110, 50, 20).intersect(clip).empty());
    }
}
