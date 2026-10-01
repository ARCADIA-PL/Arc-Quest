package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.guide.api.GuideCategory;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.builder.GuideBuilder;
import org.arcadia.arc_quest.guide.builder.GuidePageBuilder;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GuideListLayoutTest {
    @BeforeAll static void bootstrap() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void emptySearchResultsEvaluateEachGuideOnlyOnceAcrossManyCategories() {
        var categories = new ArrayList<GuideCategory>();
        var guides = new ArrayList<GuideDefinition>();
        for (int i = 0; i < 64; i++) {
            var category = category("category_" + i, i);
            categories.add(category);
            guides.add(guide("guide_" + i, category));
        }
        AtomicInteger calls = new AtomicInteger();
        assertTrue(GuideListLayout.visibleCategories(categories, guides, candidate -> {
            calls.incrementAndGet();
            return false;
        }).isEmpty());
        assertEquals(guides.size(), calls.get(), "Empty search must not multiply resolver calls by category count");
    }

    @Test void categoryOrderAndDynamicVisibilityArePreservedWithoutRetainingOldResults() {
        var first = category("a", 1);
        var tied = category("b", 1);
        var last = category("last", 3);
        var missing = category("unregistered", 0);
        var a = guide("a", first);
        var b = guide("b", tied);
        var c = guide("c", last);
        var unknown = guide("unknown", missing);
        var categories = List.of(last, tied, first);
        var guides = List.of(c, b, unknown, a);
        Set<ResourceLocation> allowed = new HashSet<>(Set.of(a.getId(), b.getId(), c.getId(), unknown.getId()));
        assertEquals(List.of(first, tied, last),
                GuideListLayout.visibleCategories(categories, guides, guide -> allowed.contains(guide.getId())));
        allowed.clear();
        allowed.add(b.getId());
        assertEquals(List.of(tied),
                GuideListLayout.visibleCategories(categories, guides, guide -> allowed.contains(guide.getId())));
        allowed.clear();
        assertTrue(GuideListLayout.visibleCategories(categories, guides, guide -> allowed.contains(guide.getId())).isEmpty());
    }

    @Test void anAlreadyMatchedCategoryDoesNotResolveAdditionalGuideText() {
        var category = category("matched", 0);
        AtomicInteger calls = new AtomicInteger();
        var guides = List.of(guide("first", category), guide("second", category));
        assertEquals(List.of(category), GuideListLayout.visibleCategories(List.of(category), guides, guide -> {
            calls.incrementAndGet();
            return true;
        }));
        assertEquals(1, calls.get());
        assertTrue(GuideListLayout.visibleCategories(List.of(), guides, guide -> {
            fail("No category means no visibility/search work");
            return true;
        }).isEmpty());
    }

    private static GuideCategory category(String id, int order) {
        return new GuideCategory(ResourceLocation.parse("test:" + id), Component.literal(id),
                0xFFFFFF, order, false, null);
    }

    private static GuideDefinition guide(String id, GuideCategory category) {
        return GuideBuilder.create("test:" + id).category(category).title(id)
                .page(GuidePageBuilder.create().description("Page")).build();
    }
}
