package org.arcadia.arc_quest.client.hud.quest.journal.history;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

final class QuestChangeHistoryPersistence {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "arc_quest_change_history.json";

    private QuestChangeHistoryPersistence() {
    }

    static HistoryFile load(Path file) {
        if (file == null || !Files.exists(file)) return new HistoryFile();
        try {
            HistoryFile data = GSON.fromJson(Files.readString(file), HistoryFile.class);
            if (data == null) return new HistoryFile();
            if (data.entries == null) data.entries = new ArrayList<>();
            if (data.maxEntries <= 0) data.maxEntries = 500;
            return data;
        } catch (Exception e) {
            try {
                Files.move(file, file.resolveSibling(FILE_NAME + ".broken"), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {
            }
            ArcQuestLog.warn(ArcQuestLog.Category.QUEST, "Failed to load quest change history, starting fresh.", e);
            return new HistoryFile();
        }
    }

    static void save(Path file, HistoryFile data) {
        if (file == null || data == null) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(data));
        } catch (Exception e) {
            ArcQuestLog.warn(ArcQuestLog.Category.QUEST, "Failed to save quest change history.", e);
        }
    }

    static Path filePath() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || !mc.hasSingleplayerServer()) return null;
            var server = mc.getSingleplayerServer();
            if (server == null) return null;
            Path root = server.getWorldPath(LevelResource.ROOT);
            return root != null ? root.resolve("data").resolve("arc_quest").resolve(FILE_NAME) : null;
        } catch (Exception e) {
            return null;
        }
    }

    static final class HistoryFile {
        int schemaVersion = 1;
        int maxEntries = 500;
        List<QuestChangeHistoryEntry> entries = new ArrayList<>();
    }
}
