package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CollectionCatalogOrderTest {
    private static final ResourceLocation COW = ResourceLocation.parse("test:cow");
    private static final ResourceLocation COAL = ResourceLocation.parse("test:coal");
    private static final ResourceLocation BONE = ResourceLocation.parse("test:bone");
    private static final List<ResourceLocation> ENTRIES = List.of(COW, COAL, BONE);

    @Test void clickingABookmarkDoesNotMoveTheCardUnderThePointerUntilARealRefresh() {
        var order = new CollectionCatalogOrder();
        var bookmarks = new AtomicReference<Set<ResourceLocation>>(Set.of());
        Object progress = new Object();
        order.refresh(progress, "all", bookmarks::get);
        assertEquals(ENTRIES, order.sort(ENTRIES, id -> id));
        bookmarks.set(Set.of(BONE));
        order.refresh(progress, "all", bookmarks::get);
        assertEquals(ENTRIES, order.sort(ENTRIES, id -> id));
        order.refresh(progress, "materials", bookmarks::get);
        assertEquals(List.of(BONE, COW, COAL), order.sort(ENTRIES, id -> id));
        bookmarks.set(Set.of(COAL));
        order.refresh(new Object(), "materials", bookmarks::get);
        assertEquals(List.of(COAL, COW, BONE), order.sort(ENTRIES, id -> id));
    }

    @Test void reopeningRefreshesFavoritesAndKeepsOriginalOrderWithinBothGroups() {
        var order = new CollectionCatalogOrder();
        var bookmarks = new AtomicReference<Set<ResourceLocation>>(Set.of(COAL, BONE));
        var reads = new AtomicInteger();
        Object progress = new Object();
        order.refresh(progress, "all", () -> { reads.incrementAndGet(); return bookmarks.get(); });
        assertEquals(List.of(COAL, BONE, COW), order.sort(ENTRIES, id -> id));
        for (int frame = 0; frame < 100; frame++)
            order.refresh(progress, "all", () -> { reads.incrementAndGet(); return bookmarks.get(); });
        assertEquals(1, reads.get());
        bookmarks.set(Set.of(COW));
        order.reset();
        order.refresh(progress, "all", bookmarks::get);
        assertEquals(ENTRIES, order.sort(ENTRIES, id -> id));
    }
}
