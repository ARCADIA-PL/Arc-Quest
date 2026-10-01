package org.arcadia.arc_quest.client.hud.shop;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class TradeIngredientSlotLayoutTest {
    @Test void inlineCostsKeepTheirNaturalWidthsAtTheLeftEdge() {
        var pages = TradeIngredientSlotLayout.inlinePages(java.util.List.of(38, 52), 400, 10);
        assertEquals(1, pages.size());
        assertEquals(java.util.List.of(new TradeIngredientSlotLayout.InlineCell(0, 0, 38),
                new TradeIngredientSlotLayout.InlineCell(1, 48, 52)), pages.get(0).cells());
        var single = TradeIngredientSlotLayout.inlinePages(java.util.List.of(38), 400, 10);
        assertEquals(0, single.get(0).cells().get(0).x());
        assertEquals(38, single.get(0).cells().get(0).width());
    }
    @Test void inlinePagingKeepsAllThirtySevenCostsReachableWithoutEqualWidthColumns() {
        var widths = java.util.stream.IntStream.range(0, 37).map(i -> 25 + i % 3 * 10).boxed().toList();
        for (int availableWidth : new int[] {20, 30, 72, 240}) {
            var pages = TradeIngredientSlotLayout.inlinePages(widths, availableWidth, 10);
            int nextIndex = 0;
            for (var page : pages) {
                int previousRight = -10;
                for (var cell : page.cells()) {
                    assertEquals(nextIndex++, cell.index());
                    assertEquals(previousRight + 10, cell.x());
                    assertEquals(Math.min(availableWidth, widths.get(cell.index())), cell.width());
                    assertTrue(cell.x() + cell.width() <= availableWidth);
                    previousRight = cell.x() + cell.width();
                }
            }
            assertEquals(37, nextIndex);
        }
    }
    @Test void inlinePagingReservesQuantityWidthBeforeAllocatingArrows() {
        var paging = TradeIngredientSlotLayout.paging(30, 10, 24);
        assertEquals(new TradeIngredientSlotLayout.Area(3, 0, 24, 10), paging.content());
        var wheelOnly = TradeIngredientSlotLayout.paging(20, 10, 24);
        assertEquals(new TradeIngredientSlotLayout.Area(0, 0, 20, 10), wheelOnly.content());
        assertEquals(0, wheelOnly.previous().width());
    }
    @Test void completedOpeningPlacesFirstMiddleAndLastOfSixHundredCards() {
        for (int index : new int[] {0, 299, 599})
            assertEquals(1f, TradeGridAnimation.openingProgress(1, index, 600));
        assertEquals(.45f + 599 * .025f, TradeGridAnimation.duration(600));
        assertEquals(0f, TradeGridAnimation.openingProgress(0, 0, 600));
        assertEquals(0f, TradeGridAnimation.openingProgress(.5f, 599, 600));
        assertEquals(1f, TradeGridAnimation.openingProgress(.5f, 0, 600));
    }
    @Test void viewportCullingUsesCurrentAnimatedBoundsAndIncludesOvershootAndHalo() {
        assertTrue(TradeGridAnimation.intersectsViewport(20, 20, 40, 30, 1, 400, 240));
        assertFalse(TradeGridAnimation.intersectsViewport(-1000, 20, 40, 30, 1, 400, 240));
        assertTrue(TradeGridAnimation.intersectsViewport(-52, 20, 40, 30, 1.2f, 400, 240));
        assertTrue(TradeGridAnimation.intersectsViewport(398, 20, 40, 30, 1, 400, 240));
        assertFalse(TradeGridAnimation.intersectsViewport(20, 300, 40, 30, 1.06f, 400, 240));
    }
    @Test void normalCostsStayIndividuallyClickableWithoutPaging() {
        for (int count = 1; count <= 4; count++) {
            var cells = TradeIngredientSlotLayout.fit(count, 72, 18, 14);
            assertEquals(count, cells.size());
            assertTrue(cells.stream().allMatch(cell -> cell.iconSize() >= 12));
            assertDisjointInside(cells, 72, 18);
        }
    }
    @Test void compositeRewardsWrapIntoSeparateRows() {
        var cells = TradeIngredientSlotLayout.fit(4, 38, 34, 20);
        assertEquals(4, cells.size());
        assertTrue(cells.get(2).y() > cells.get(0).y());
        assertTrue(cells.stream().allMatch(cell -> cell.iconSize() >= 12));
        assertDisjointInside(cells, 38, 34);
    }
    @Test void thirtyByTwentyPagingReservesArrowsWithoutExpandingTheStrip() {
        var paging = TradeIngredientSlotLayout.paging(30, 20);
        assertEquals(new TradeIngredientSlotLayout.Area(8, 0, 14, 20), paging.content());
        assertEquals(new TradeIngredientSlotLayout.Area(0, 0, 8, 20), paging.previous());
        assertEquals(new TradeIngredientSlotLayout.Area(22, 0, 8, 20), paging.next());
        assertPagingInside(paging, 30, 20, 1);
    }
    @Test void twentyByTwentyPagingShrinksArrowsAndKeepsItemHitsSeparate() {
        var paging = TradeIngredientSlotLayout.paging(20, 20);
        assertEquals(new TradeIngredientSlotLayout.Area(3, 0, 14, 20), paging.content());
        assertEquals(new TradeIngredientSlotLayout.Area(0, 0, 3, 20), paging.previous());
        assertEquals(new TradeIngredientSlotLayout.Area(17, 0, 3, 20), paging.next());
        assertPagingInside(paging, 20, 20, 1);
    }
    @Test void tallPagingReservesArrowsAlongTheLongerAxis() {
        var paging = TradeIngredientSlotLayout.paging(20, 30);
        assertEquals(new TradeIngredientSlotLayout.Area(0, 8, 20, 14), paging.content());
        assertEquals(new TradeIngredientSlotLayout.Area(0, 0, 20, 8), paging.previous());
        assertEquals(new TradeIngredientSlotLayout.Area(0, 22, 20, 8), paging.next());
        assertPagingInside(paging, 20, 30, 1);
    }
    @Test void tinyAreasKeepTheirDimensionsAndUseWheelOnlyPaging() {
        var paging = TradeIngredientSlotLayout.paging(12, 12);
        assertEquals(new TradeIngredientSlotLayout.Area(0, 0, 12, 12), paging.content());
        assertEquals(0, paging.previous().width());
        assertEquals(0, paging.next().width());
        assertPagingInside(paging, 12, 12, 1);
    }
    @Test void overflowPagesKeepAllThirtySevenCostsReachableAtMinimumSize() {
        int count = 37;
        for (int[] dimensions : new int[][] {{72, 18}, {30, 20}, {20, 20}, {20, 30}}) {
            int width = dimensions[0], height = dimensions[1];
            var paging = TradeIngredientSlotLayout.paging(width, height);
            var body = paging.content();
            int capacity = TradeIngredientSlotLayout.pageCapacity(body.width(), body.height());
            var visited = new HashSet<Integer>();
            for (int start = 0; start < count; start += capacity) {
                int visibleCount = Math.min(capacity, count - start);
                var cells = TradeIngredientSlotLayout.fit(visibleCount, body.width(), body.height(), 14);
                assertEquals(visibleCount, cells.size());
                assertTrue(cells.stream().allMatch(cell -> cell.iconSize() >= 12));
                assertPagingInside(paging, width, height, visibleCount);
                for (int i = 0; i < cells.size(); i++) assertTrue(visited.add(start + i));
            }
            assertEquals(count, visited.size());
            assertTrue(visited.contains(count - 1));
        }
    }
    @Test void emptyOrCollapsedAreaCreatesNoInteractiveSlots() {
        assertTrue(TradeIngredientSlotLayout.fit(0, 80, 20, 14).isEmpty());
        assertTrue(TradeIngredientSlotLayout.fit(2, 0, 20, 14).isEmpty());
        assertTrue(TradeIngredientSlotLayout.fit(2, 80, 0, 14).isEmpty());
    }
    private static void assertPagingInside(TradeIngredientSlotLayout.Paging paging, int width, int height, int count) {
        var body = paging.content();
        for (var area : java.util.List.of(body, paging.previous(), paging.next())) {
            assertTrue(area.x() >= 0 && area.y() >= 0 && area.width() >= 0 && area.height() >= 0);
            assertTrue(area.x() + area.width() <= width && area.y() + area.height() <= height);
        }
        var cells = TradeIngredientSlotLayout.fit(count, body.width(), body.height(), 14);
        assertDisjointInside(cells, body.width(), body.height());
        for (var cell : cells) {
            double left = body.x() + cell.x() + (cell.width() - cell.iconSize()) / 2;
            double top = body.y() + cell.y() + (cell.height() - cell.iconSize()) / 2;
            assertTrue(left >= 0 && top >= 0 && left + cell.iconSize() <= width && top + cell.iconSize() <= height);
            for (var arrow : java.util.List.of(paging.previous(), paging.next()))
                assertTrue(left + cell.iconSize() <= arrow.x() || arrow.x() + arrow.width() <= left
                        || top + cell.iconSize() <= arrow.y() || arrow.y() + arrow.height() <= top);
        }
    }
    private static void assertDisjointInside(java.util.List<TradeIngredientSlotLayout.Cell> cells, int width, int height) {
        for (int i = 0; i < cells.size(); i++) {
            var a = cells.get(i);
            assertTrue(a.x() >= 0 && a.y() >= 0);
            assertTrue(a.x() + a.width() <= width + .0001 && a.y() + a.height() <= height + .0001);
            for (int j = i + 1; j < cells.size(); j++) {
                var b = cells.get(j);
                assertTrue(a.x() + a.width() <= b.x() + .0001 || b.x() + b.width() <= a.x() + .0001
                        || a.y() + a.height() <= b.y() + .0001 || b.y() + b.height() <= a.y() + .0001);
            }
        }
    }
}
