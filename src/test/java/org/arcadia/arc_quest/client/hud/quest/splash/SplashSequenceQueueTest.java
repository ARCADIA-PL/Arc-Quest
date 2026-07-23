package org.arcadia.arc_quest.client.hud.quest.splash;

import org.arcadia.arc_quest.quest.api.SplashType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplashSequenceQueueTest {

    @Test
    void queuedSplashesPlayInFifoOrderWithoutReplacingActive() {
        SplashSequenceQueue<String> queue = new SplashSequenceQueue<>(4);

        assertTrue(queue.enqueue("quest_complete_a"));
        assertTrue(queue.enqueue("quest_start_b"));
        assertTrue(queue.enqueue("phase_start_b"));

        assertEquals("quest_complete_a", queue.current());
        assertEquals(2, queue.pendingCount());
        assertEquals("quest_start_b", queue.completeActive());
        assertEquals("phase_start_b", queue.completeActive());
        assertNull(queue.completeActive());
        assertFalse(queue.isActive());
    }

    @Test
    void pendingCapacityIsBoundedAndClearRemovesAllState() {
        SplashSequenceQueue<String> queue = new SplashSequenceQueue<>(1);

        assertTrue(queue.enqueue("active"));
        assertTrue(queue.enqueue("pending"));
        assertFalse(queue.enqueue("rejected"));

        queue.clear();

        assertFalse(queue.isActive());
        assertEquals(0, queue.pendingCount());
        assertNull(queue.current());
    }

    @Test
    void phaseQuestPresentationUsesMatchingQuestStatusText() {
        assertEquals(SplashType.QUEST_ACQUIRED,
                QuestSplashRenderer.toQuestSplashType(SplashType.PHASE_START));
        assertEquals(SplashType.QUEST_COMPLETED,
                QuestSplashRenderer.toQuestSplashType(SplashType.PHASE_COMPLETE));
        assertEquals(SplashType.QUEST_FAILED,
                QuestSplashRenderer.toQuestSplashType(SplashType.QUEST_FAILED));
    }
}
