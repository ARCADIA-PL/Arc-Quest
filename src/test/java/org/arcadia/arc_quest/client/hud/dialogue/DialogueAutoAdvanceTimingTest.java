package org.arcadia.arc_quest.client.hud.dialogue;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DialogueAutoAdvanceTimingTest {
    @Test void shortLinesAndEmptyAliasesNoLongerWaitSevenSeconds() {
        assertEquals(3000, DialogueAutoAdvanceTiming.delay("一二三四五六七八九十", 800));
        assertEquals(3000, DialogueAutoAdvanceTiming.delay("你好", 800));
        assertEquals(600, DialogueAutoAdvanceTiming.delay("", 0));
        assertEquals(600, DialogueAutoAdvanceTiming.delay(" \n\t\u3000", -100));
        assertEquals(800, DialogueAutoAdvanceTiming.delay("", 800));
    }

    @Test void readingBudgetGrowsWithVisibleTextAndStopsAtTwentyFourSeconds() {
        assertEquals(7200, DialogueAutoAdvanceTiming.delay("文".repeat(50), 800));
        assertEquals(13200, DialogueAutoAdvanceTiming.delay("文".repeat(100), 800));
        assertEquals(24000, DialogueAutoAdvanceTiming.delay("文".repeat(200), 800));
        assertEquals(24000, DialogueAutoAdvanceTiming.delay("文".repeat(100000), 800));
    }

    @Test void formattingWhitespaceAndSurrogatePairsDoNotInflateReadingTime() {
        assertEquals(DialogueAutoAdvanceTiming.delay("文".repeat(50), 0),
                DialogueAutoAdvanceTiming.delay("§6文 \n\t\u3000§r".repeat(50), 0));
        assertEquals(7200, DialogueAutoAdvanceTiming.delay("\uD83D\uDE00".repeat(50), 0));
    }

    @Test void authoredPausesStayWithinBoundsAndNeverShortenReadingTime() {
        assertEquals(15000, DialogueAutoAdvanceTiming.delay("你好", 15000));
        assertEquals(13200, DialogueAutoAdvanceTiming.delay("文".repeat(100), 10000));
        assertEquals(30000, DialogueAutoAdvanceTiming.delay("你好", Integer.MAX_VALUE));
        assertEquals(3000, DialogueAutoAdvanceTiming.delay("你好", Integer.MIN_VALUE));
    }

    @Test void typingTimeCountsTowardsTheSamePageBudget() {
        var clock = new DialogueAutoAdvanceTiming();
        int budget = DialogueAutoAdvanceTiming.delay("文".repeat(50), 800);
        for (int i = 0; i < 10; i++) assertFalse(clock.tick(100, false, false, budget));
        for (int i = 0; i < 61; i++) assertFalse(clock.tick(100, false, true, budget));
        assertTrue(clock.tick(100, false, true, budget)); // 7.2 seconds total, including typing.
    }

    @Test void expiredBudgetNeverCutsOffTypingOrTheLastCharacters() {
        var clock = new DialogueAutoAdvanceTiming();
        for (int i = 0; i < 100; i++) assertFalse(clock.tick(100, false, false, 3000));
        assertFalse(clock.tick(100, false, true, 3000)); // First fully visible frame starts the hold.
        for (int i = 0; i < 5; i++) assertFalse(clock.tick(100, false, true, 3000));
        assertTrue(clock.tick(100, false, true, 3000));
    }

    @Test void pauseFreezesBothReadingAndFullTextHold() {
        var clock = new DialogueAutoAdvanceTiming();
        for (int i = 0; i < 23; i++) assertFalse(clock.tick(100, false, false, 3000));
        for (int i = 0; i < 100; i++) assertFalse(clock.tick(100, true, false, 3000));
        assertFalse(clock.tick(100, false, true, 3000));
        assertFalse(clock.tick(100, false, true, 3000));
        for (int i = 0; i < 100; i++) assertFalse(clock.tick(100, true, true, 3000));
        for (int i = 0; i < 4; i++) assertFalse(clock.tick(100, false, true, 3000));
        assertTrue(clock.tick(100, false, true, 3000));
    }

    @Test void newNodeResetsBothClocks() {
        var clock = new DialogueAutoAdvanceTiming();
        for (int i = 0; i < 30; i++) clock.tick(100, false, true, 3000);
        clock.reset();
        for (int i = 0; i < 29; i++) assertFalse(clock.tick(100, false, true, 3000));
        assertTrue(clock.tick(100, false, true, 3000));
    }

    @Test void stalledOrNegativeFrameCannotSkipThePage() {
        var clock = new DialogueAutoAdvanceTiming();
        assertFalse(clock.tick(10000, false, true, 3000));
        assertFalse(clock.tick(-10000, false, true, 3000));
        for (int i = 0; i < 28; i++) assertFalse(clock.tick(100, false, true, 3000));
        assertTrue(clock.tick(100, false, true, 3000));
    }

    @Test void revealingTextEarlyDoesNotRestartTheBudget() {
        var clock = new DialogueAutoAdvanceTiming();
        for (int i = 0; i < 8; i++) assertFalse(clock.tick(100, false, false, 3000));
        for (int i = 0; i < 21; i++) assertFalse(clock.tick(100, false, true, 3000));
        assertTrue(clock.tick(100, false, true, 3000));
    }
}
