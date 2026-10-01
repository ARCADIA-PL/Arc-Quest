package org.arcadia.arc_quest.client.hud.dialogue;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DialogueHistoryViewportTest {
    @Test void findsFirstVisibleBlockAndRetainsTouchingBoundaries() {
        int[] bottoms = {40, 80, 140, 200};
        assertEquals(0, DialogueHistoryViewport.firstBlock(bottoms, 0));
        assertEquals(0, DialogueHistoryViewport.firstBlock(bottoms, 40));
        assertEquals(1, DialogueHistoryViewport.firstBlock(bottoms, 40.5f));
        assertEquals(3, DialogueHistoryViewport.firstBlock(bottoms, 199));
        assertEquals(4, DialogueHistoryViewport.firstBlock(bottoms, 201));
        assertEquals(0, DialogueHistoryViewport.firstBlock(new int[0], 0));
    }
    @Test void boundsLongTextToVisibleLinesWithOneLineOverscan() {
        assertEquals(9, DialogueHistoryViewport.firstLine(160, 0, 16, 1000));
        assertEquals(14, DialogueHistoryViewport.lastLineExclusive(208, 0, 16, 1000));
        assertEquals(0, DialogueHistoryViewport.firstLine(0, 30, 16, 10));
        assertEquals(10, DialogueHistoryViewport.lastLineExclusive(1000, 30, 16, 10));
    }
}
