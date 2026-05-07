package org.arcadia.arc_quest.quest.spec.io;

import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class QuestSpecResourceLoader2Test {

    @AfterEach
    void cleanup() {
        System.clearProperty(DatapackPathResolver.DATAPACK_DIR_PROPERTY);
    }

    @Test
    void loadFromDatapack_shouldLoadValidAndReportInvalid(@TempDir Path tempDir) throws Exception {
        System.setProperty(DatapackPathResolver.DATAPACK_DIR_PROPERTY, tempDir.toString());
        Path quests = DatapackPathResolver.resolveQuestsDir();
        Files.createDirectories(quests);

        Files.writeString(quests.resolve("ok.json"), "{\"id\":\"arc_quest:test_ok\",\"category\":\"ADVENTURE\",\"phases\":[]}");
        Files.writeString(quests.resolve("bad.json"), "{ this is invalid json }");

        QuestSpecResourceLoader2 loader = new QuestSpecResourceLoader2();
        QuestSpecResourceLoader2.LoadReport report = loader.loadFromDatapack();

        assertEquals(2, report.scannedFiles());
        assertEquals(1, report.loadedCount());
        assertEquals(1, report.failedCount());

        QuestSpec loaded = report.specs().values().iterator().next();
        assertEquals("arc_quest:test_ok", loaded.id);
    }
}
