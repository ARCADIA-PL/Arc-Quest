package org.arcadia.arc_quest.quest.spec.io;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class DatapackPathResolver {

    public static final String DATAPACK_DIR_PROPERTY = "arcquest.datapack.dir";

    private DatapackPathResolver() {
    }

    public static Path resolveDatapackRoot() {
        String configured = System.getProperty(DATAPACK_DIR_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured).toAbsolutePath().normalize();
        }
        return Paths.get("arc_quest", "datapack").toAbsolutePath().normalize();
    }

    public static Path resolveQuestsDir() {
        return resolveDatapackRoot().resolve("quests").normalize();
    }

    public static Path resolveQuestFile(String questFileName) {
        if (questFileName == null || questFileName.isBlank()) {
            throw new IllegalArgumentException("questFileName must not be blank");
        }
        if (questFileName.contains("..") || questFileName.contains("/") || questFileName.contains("\\")) {
            throw new IllegalArgumentException("questFileName must be a plain file name without path traversal");
        }
        String normalized = questFileName.endsWith(".json") ? questFileName : (questFileName + ".json");
        Path target = resolveQuestsDir().resolve(normalized).normalize();
        ensureInsideDatapackRoot(target);
        return target;
    }

    public static void ensureInsideDatapackRoot(Path target) {
        Path root = resolveDatapackRoot();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes datapack root: " + normalizedTarget);
        }
    }

    public static void ensureDatapackDirsExist() throws java.io.IOException {
        Files.createDirectories(resolveQuestsDir());
    }
}
