package org.arcadia.arc_quest.client.hud.quest.icon.portrait;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoundedPortraitCacheTest {
    @Test void recentlyUsedHeadIsSharedAndOnlyEvictedResourcesAreReleased() {
        var released = new ArrayList<String>();
        var cache = new BoundedPortraitCache<String, String>(2, released::add);
        cache.computeIfAbsent("zombie", ignored -> "texture-a");
        cache.computeIfAbsent("piglin", ignored -> "texture-b");
        assertEquals("texture-a", cache.computeIfAbsent("zombie", ignored -> fail("Must reuse the shared texture")));
        cache.computeIfAbsent("dragon", ignored -> "texture-c");
        assertEquals(List.of("texture-b"), released);
        assertNull(cache.get("piglin"));
        assertEquals(2, cache.size());
        cache.clear();
        cache.clear();
        assertEquals(List.of("texture-b", "texture-a", "texture-c"), released);
    }

    @Test void failedFactoryDoesNotEvictAnExistingPortrait() {
        var released = new ArrayList<String>();
        var cache = new BoundedPortraitCache<String, String>(1, released::add);
        cache.computeIfAbsent("valid", ignored -> "texture");
        assertThrows(IllegalArgumentException.class, () -> cache.computeIfAbsent("broken", ignored -> { throw new IllegalArgumentException(); }));
        assertEquals("texture", cache.get("valid"));
        assertTrue(released.isEmpty());
    }
}
