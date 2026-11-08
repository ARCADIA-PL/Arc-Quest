package org.arcadia.arc_quest.client.hud.dialogue;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DialogueAutoAdvanceTimingTest {
    @Test void shortAndEmptyPagesWaitAtLeastSevenSeconds() {
        assertEquals(7000, DialogueAutoAdvanceTiming.delay("一二三四五六七八九十", 800));
        assertEquals(7000, DialogueAutoAdvanceTiming.delay("", 0));
        assertEquals(7000, DialogueAutoAdvanceTiming.delay(" \n\t\u3000", -100));
    }

    @Test void readingTimeGrowsWithVisibleTextAndStopsAtThirtySeconds() {
        assertEquals(11000, DialogueAutoAdvanceTiming.delay("文".repeat(50), 800));
        assertEquals(20000, DialogueAutoAdvanceTiming.delay("文".repeat(100), 800));
        assertEquals(30000, DialogueAutoAdvanceTiming.delay("文".repeat(200), 800));
        assertEquals(30000, DialogueAutoAdvanceTiming.delay("文".repeat(100000), 800));
    }

    @Test void formattingWhitespaceAndSurrogatePairsDoNotInflateReadingTime() {
        assertEquals(DialogueAutoAdvanceTiming.delay("文".repeat(50), 0),
                DialogueAutoAdvanceTiming.delay("§6文 \n\t\u3000§r".repeat(50), 0));
        assertEquals(11000, DialogueAutoAdvanceTiming.delay("\uD83D\uDE00".repeat(50), 0));
    }

    @Test void authoredPausesStayWithinBoundsAndNeverShortenReadingTime() {
        assertEquals(15000, DialogueAutoAdvanceTiming.delay("你好", 15000));
        assertEquals(20000, DialogueAutoAdvanceTiming.delay("文".repeat(100), 10000));
        assertEquals(30000, DialogueAutoAdvanceTiming.delay("你好", Integer.MAX_VALUE));
        assertEquals(7000, DialogueAutoAdvanceTiming.delay("你好", Integer.MIN_VALUE));
    }
}
