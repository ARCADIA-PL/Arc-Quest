package org.arcadia.arc_quest.quest.spec.io;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DatapackPathResolverTest {

    @AfterEach
    void cleanup() {
        System.clearProperty(DatapackPathResolver.DATAPACK_DIR_PROPERTY);
    }

    @Test
    void resolveDatapackRoot_shouldUseConfiguredProperty(@TempDir Path tempDir) {
        System.setProperty(DatapackPathResolver.DATAPACK_DIR_PROPERTY, tempDir.toString());
        assertEquals(tempDir.toAbsolutePath().normalize(), DatapackPathResolver.resolveDatapackRoot());
    }

    @Test
    void resolveQuestFile_shouldRejectPathTraversal(@TempDir Path tempDir) {
        System.setProperty(DatapackPathResolver.DATAPACK_DIR_PROPERTY, tempDir.toString());
        assertThrows(IllegalArgumentException.class, () -> DatapackPathResolver.resolveQuestFile("..\\evil"));
        assertThrows(IllegalArgumentException.class, () -> DatapackPathResolver.resolveQuestFile("a/b"));
    }

    @Test
    void ensureDatapackDirsExist_shouldCreateQuestsDir(@TempDir Path tempDir) throws Exception {
        System.setProperty(DatapackPathResolver.DATAPACK_DIR_PROPERTY, tempDir.toString());
        DatapackPathResolver.ensureDatapackDirsExist();
        assertTrue(java.nio.file.Files.exists(DatapackPathResolver.resolveQuestsDir()));
    }
}
