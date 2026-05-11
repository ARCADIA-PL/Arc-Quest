package org.arcadia.arc_quest.dialogue.io;

import org.arcadia.arc_quest.quest.spec.io.DatapackPathResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 预留给 dialogue datapack 的目录扫描器。
 * <p>
 * 当前阶段只负责发现和统计 datapack 下的对话 JSON 文件，
 * 让 reload 链先稳定到 quest-style 的「dialogue datapack layer」语义。
 */
public final class DialogueDatapackResourceLoader {

    public LoadReport scan() {
        Path dialoguesDir = DatapackPathResolver.resolveDialoguesDir();
        List<Path> files = new ArrayList<>();
        List<Path> failed = new ArrayList<>();

        if (!Files.exists(dialoguesDir)) {
            return new LoadReport(dialoguesDir, files, failed, 0);
        }

        int scanned = 0;
        try (var stream = Files.walk(dialoguesDir)) {
            for (Path file : (Iterable<Path>) stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))::iterator) {
                scanned++;
                try {
                    files.add(file.toAbsolutePath().normalize());
                } catch (Exception ex) {
                    failed.add(file.toAbsolutePath().normalize());
                }
            }
        } catch (IOException ex) {
            failed.add(dialoguesDir.toAbsolutePath().normalize());
        }

        return new LoadReport(dialoguesDir, files, failed, scanned);
    }

    public record LoadReport(Path rootDir, List<Path> files, List<Path> failedFiles, int scannedFiles) {
        public int loadedCount() {
            return files.size();
        }

        public int failedCount() {
            return failedFiles.size();
        }
    }
}
