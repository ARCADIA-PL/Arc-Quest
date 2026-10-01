package org.arcadia.arc_quest.integration.jei.trade;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChapterShopVisibilityTest {
    @Test void chapterShopRequiresActiveOrPersistentCompletedQuest() {
        assertFalse(ChapterShopVisibility.canAccess(false, false, false));
        assertFalse(ChapterShopVisibility.canAccess(false, false, true));
        assertFalse(ChapterShopVisibility.canAccess(false, true, false));
        assertTrue(ChapterShopVisibility.canAccess(true, false, false));
        assertTrue(ChapterShopVisibility.canAccess(false, true, true));
    }
}
