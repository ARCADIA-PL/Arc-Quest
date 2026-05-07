package org.arcadia.arc_quest.quest.spec.io;

import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class QuestDatapackWriterTest {

    @AfterEach
    void cleanup() {
        System.clearProperty(DatapackPathResolver.DATAPACK_DIR_PROPERTY);
    }

    @Test
    void write_shouldWriteQuestJsonToDatapack(@TempDir Path tempDir) throws Exception {
        System.setProperty(DatapackPathResolver.DATAPACK_DIR_PROPERTY, tempDir.toString());

        QuestSpec spec = new QuestSpec();
        spec.id = "arc_quest:writer_test";

        QuestDatapackWriter writer = new QuestDatapackWriter();
        Path out = writer.write("writer_test", spec);

        assertTrue(Files.exists(out));
        String json = Files.readString(out);
        assertTrue(json.contains("arc_quest:writer_test"));
    }

    @Test
    void write_shouldRejectInvalidQuestFileName(@TempDir Path tempDir) {
        System.setProperty(DatapackPathResolver.DATAPACK_DIR_PROPERTY, tempDir.toString());

        QuestDatapackWriter writer = new QuestDatapackWriter();
        QuestSpec spec = new QuestSpec();
        spec.id = "arc_quest:writer_test";

        assertThrows(IllegalArgumentException.class, () -> {
            try {
                writer.write("../escape", spec);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
