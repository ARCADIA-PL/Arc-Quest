package org.arcadia.arc_quest.data;

import org.arcadia.arc_quest.quest.spec.io.DatapackPathResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ArcQuestDatapackHotReloadServiceTest {

    @AfterEach
    void cleanup() {
        System.clearProperty(DatapackPathResolver.DATAPACK_DIR_PROPERTY);
    }

    @Test
    void reload_shouldUseDatapackPathWithoutFallback(@TempDir Path tempDir) throws Exception {
        System.setProperty(DatapackPathResolver.DATAPACK_DIR_PROPERTY, tempDir.toString());
        Path quests = DatapackPathResolver.resolveQuestsDir();
        Files.createDirectories(quests);
        Files.writeString(quests.resolve("phase1_reload_test.json"), """
                {
                  "id": "arc_quest:phase1_reload_test",
                  "category": "ADVENTURE",
                  "displayName": {"mode":"LITERAL","value":"Reload Test"},
                  "description": {"mode":"LITERAL","value":"Reload Test"},
                  "initialPhaseId": "arc_quest:start",
                  "phases": [
                    {
                      "phaseId":"arc_quest:start",
                      "displayName":{"mode":"LITERAL","value":"Start"},
                      "objectives": []
                    }
                  ]
                }
                """);

        ArcQuestDatapackHotReloadService service = new ArcQuestDatapackHotReloadService();
        ArcQuestDatapackHotReloadService.ReloadResult result = service.reload(null);

        assertFalse(result.usedFallback());
        assertTrue(result.scanned() >= 1);
        assertTrue(result.failed() >= 1 || result.loaded() >= 1);
    }
}
