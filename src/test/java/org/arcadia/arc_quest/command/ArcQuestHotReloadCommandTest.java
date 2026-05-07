package org.arcadia.arc_quest.command;

import org.arcadia.arc_quest.data.ArcQuestDatapackHotReloadService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ArcQuestHotReloadCommandTest {

    @Test
    void formatReloadMessage_shouldContainAllKeyFields() {
        var result = new ArcQuestDatapackHotReloadService.ReloadResult(12, 9, 3, 9, 20, false);
        String msg = ArcQuestHotReloadCommand.formatReloadMessage(result);

        assertTrue(msg.contains("scanned=12"));
        assertTrue(msg.contains("loaded=9"));
        assertTrue(msg.contains("failed=3"));
        assertTrue(msg.contains("activeDatapack=9"));
        assertTrue(msg.contains("merged=20"));
        assertTrue(msg.contains("source=@datapack"));
    }
}
