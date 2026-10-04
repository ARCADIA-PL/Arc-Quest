package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.CollectionCategoryDefinition;
import org.arcadia.arc_quest.quest.api.CollectionEntryDefinition;
import org.arcadia.arc_quest.quest.api.CollectionQuestConfig;
import org.arcadia.arc_quest.quest.api.CollectionSheetDefinition;
import org.arcadia.arc_quest.quest.api.QuestText;
import org.arcadia.arc_quest.quest.builder.CollectionEntryBuilder;
import org.arcadia.arc_quest.quest.builder.CollectionQuestConfigBuilder;
import org.arcadia.arc_quest.quest.builder.CollectionSheetBuilder;
import org.arcadia.arc_quest.quest.builder.EntryRequirementBuilder;
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
    private static final ResourceLocation IRON = ResourceLocation.parse("test:iron");
    private static final ResourceLocation SPIDER = ResourceLocation.parse("test:spider");
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

    @Test void allViewGroupsByCategoryBeforeComparingEntryPriority() {
        var order = configured(List.of(category("materials", 20), category("animals", 10)),
                List.of(entry(COAL, "materials", -1000), entry(BONE, "animals", 20),
                        entry(COW, "animals", 10), entry(IRON, "materials", 20)),
                List.of(COAL, BONE, IRON, COW));
        assertEquals(List.of("animals", "materials"), order.categories().stream()
                .map(CollectionCategoryDefinition::getCategoryId).toList());
        assertEquals(List.of(COW, BONE, COAL, IRON), order.sort(List.of(COAL, BONE, IRON, COW), id -> id));
    }

    @Test void equalOrdersKeepCategoryDeclarationThenFirstBindingDeclarationEvenForFilteredInput() {
        var order = configured(List.of(category("materials", 0), category("animals", 0)),
                List.of(entry(BONE, "animals", 0), entry(IRON, "materials", 0),
                        entry(COAL, "materials", 0), entry(COW, "animals", 0)),
                List.of(COW, COAL, BONE, IRON));
        assertEquals(List.of(COAL, IRON, COW, BONE), order.sort(List.of(BONE, IRON, COAL, COW), id -> id));
        // Search/category filters retain author order rather than the input order or translated name.
        assertEquals(List.of(COW, BONE), order.sort(List.of(BONE, COW), id -> id));
        assertEquals(List.of(COAL, IRON), order.sort(List.of(IRON, COAL), id -> id));
    }

    @Test void missingCategoriesStayLastEvenWithExtremeSignedPriorities() {
        var order = configured(List.of(category("known", Integer.MAX_VALUE)),
                List.of(entry(COW, "unregistered", Integer.MIN_VALUE), entry(COAL, "missing", -100),
                        entry(BONE, "known", Integer.MAX_VALUE), entry(IRON, "known", Integer.MIN_VALUE)),
                List.of(COW, COAL, BONE, IRON));
        assertEquals(List.of(IRON, BONE, COW, COAL), order.sort(List.of(COW, COAL, BONE, IRON), id -> id));
    }

    @Test void favoritesUseTheSameHierarchyAndOnlyMoveAtTheNextBrowserRefresh() {
        var order = configured(List.of(category("animals", 10), category("materials", 20)),
                List.of(entry(COW, "animals", 10), entry(SPIDER, "animals", 20),
                        entry(COAL, "materials", 10), entry(IRON, "materials", 20)),
                List.of(COAL, SPIDER, IRON, COW));
        var bookmarks = new AtomicReference<Set<ResourceLocation>>(Set.of());
        Object progress = new Object();
        order.refresh(progress, "all", bookmarks::get);
        var input = List.of(COAL, SPIDER, IRON, COW);
        assertEquals(List.of(COW, SPIDER, COAL, IRON), order.sort(input, id -> id));
        bookmarks.set(Set.of(SPIDER, IRON));
        order.refresh(progress, "all", bookmarks::get);
        assertEquals(List.of(COW, SPIDER, COAL, IRON), order.sort(input, id -> id));
        order.refresh(progress, "search", bookmarks::get);
        assertEquals(List.of(SPIDER, IRON, COW, COAL), order.sort(input, id -> id));
        assertEquals(List.of(SPIDER, IRON), order.sort(List.of(IRON, SPIDER), id -> id));
    }

    @Test void investigationPriorityAndRepresentativeChangesCannotMoveAnEntryCard() {
        var config = config(List.of(category("animals", 0)), List.of(
                entry(COW, "animals", 0), entry(BONE, "animals", 0)));
        var sheet = CollectionSheetBuilder.create().all()
                .binding(EntryRequirementBuilder.create("cow_first", COW).discovered().sortOrder(50))
                .binding(EntryRequirementBuilder.create("bone", BONE).discovered().sortOrder(-100))
                .binding(EntryRequirementBuilder.create("cow_second", COW).discovered().sortOrder(-10))
                .binding(EntryRequirementBuilder.create("cow_third", COW).discovered().sortOrder(-10)).build();
        var order = new CollectionCatalogOrder();
        order.configure(config, sheet);
        assertEquals(List.of("cow_second", "cow_third", "cow_first"), order.sortBindings(
                List.of("cow_third", "cow_first", "cow_second"), id -> id));
        assertEquals(List.of(COW, BONE), order.sort(List.of(BONE, COW), id -> id));
        // Completing/replacing a representative does not make its binding's order become the card order.
        record Card(ResourceLocation entry, String representative) {}
        var first = order.sort(List.of(new Card(BONE, "bone"), new Card(COW, "cow_first")), Card::entry);
        var next = order.sort(List.of(new Card(BONE, "bone"), new Card(COW, "cow_second")), Card::entry);
        assertEquals(first.stream().map(Card::entry).toList(), next.stream().map(Card::entry).toList());
        assertEquals(List.of("cow_first", "bone", "cow_second", "cow_third"), sheet.getBindings().stream()
                .map(binding -> binding.getBindingId()).toList());
    }

    @Test void definitionChangesInvalidateCachedOrderWithoutRequiringNewProgress() {
        var entries = List.of(entry(COW, "animals", 0), entry(COAL, "materials", 0));
        var first = config(List.of(category("animals", 0), category("materials", 10)), entries);
        var next = config(List.of(category("animals", 10), category("materials", 0)), entries);
        var sheet = sheet(List.of(COW, COAL));
        var order = new CollectionCatalogOrder();
        assertTrue(order.configure(first, sheet));
        assertFalse(order.configure(first, sheet));
        Object progress = new Object();
        order.refresh(progress, "all", Set::of);
        assertEquals(List.of(COW, COAL), order.sort(List.of(COW, COAL), id -> id));
        assertTrue(order.configure(next, sheet));
        order.refresh(progress, "all", Set::of);
        assertEquals(List.of(COAL, COW), order.sort(List.of(COW, COAL), id -> id));
    }

    @Test void reorderKeepsTheScrolledTopSpecimenAndItsPartialRowOffset() {
        var previous = List.of(COW, COAL, BONE, IRON, SPIDER);
        var next = List.of(BONE, COW, SPIDER, COAL, IRON);
        assertEquals(5, CollectionCatalogOrder.preserveScroll(previous, next, id -> id, 93, 2, 2, 88));
        assertEquals(181, CollectionCatalogOrder.preserveScroll(previous, next, id -> id, 181, 2, 1, 88));
        assertEquals(93, CollectionCatalogOrder.preserveScroll(previous, List.of(COW, COAL), id -> id, 93, 2, 2, 88));
        assertEquals(0, CollectionCatalogOrder.preserveScroll(previous, next, id -> id, 0, 2, 2, 88));
    }

    private static CollectionCategoryDefinition category(String id, int order) {
        return new CollectionCategoryDefinition(id, QuestText.literal(id), null, order, List.of(), List.of(), List.of());
    }

    private static CollectionEntryDefinition entry(ResourceLocation id, String category, int order) {
        return CollectionEntryBuilder.create(id).category(category).displayName(id.getPath()).sortOrder(order).build();
    }

    private static CollectionQuestConfig config(List<CollectionCategoryDefinition> categories,
                                                 List<CollectionEntryDefinition> entries) {
        var builder = CollectionQuestConfigBuilder.create();
        categories.forEach(builder::category);
        entries.forEach(builder::entry);
        return builder.build();
    }

    private static CollectionSheetDefinition sheet(List<ResourceLocation> ids) {
        var builder = CollectionSheetBuilder.create().all();
        for (int index = 0; index < ids.size(); index++)
            builder.binding(EntryRequirementBuilder.create("binding_" + index, ids.get(index)).discovered());
        return builder.build();
    }

    private static CollectionCatalogOrder configured(List<CollectionCategoryDefinition> categories,
            List<CollectionEntryDefinition> entries, List<ResourceLocation> ids) {
        var order = new CollectionCatalogOrder();
        order.configure(config(categories, entries), sheet(ids));
        order.refresh(new Object(), "all", Set::of);
        return order;
    }
}
