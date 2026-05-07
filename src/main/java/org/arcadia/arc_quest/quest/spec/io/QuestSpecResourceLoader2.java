package org.arcadia.arc_quest.quest.spec.io;

import org.arcadia.arc_quest.quest.spec.QuestSpec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class QuestSpecResourceLoader2 {

    public record LoadReport(Map<Path, QuestSpec> specs, List<QuestDatapackLoadError> errors, int scannedFiles) {
        public int loadedCount() { return specs.size(); }
        public int failedCount() { return errors.size(); }
    }

    public LoadReport loadFromDatapack() {
        Path questsDir = DatapackPathResolver.resolveQuestsDir();
        Map<Path, QuestSpec> specs = new LinkedHashMap<>();
        List<QuestDatapackLoadError> errors = new ArrayList<>();

        if (!Files.exists(questsDir)) {
            return new LoadReport(specs, errors, 0);
        }

        int scanned = 0;
        try (var stream = Files.walk(questsDir)) {
            for (Path file : (Iterable<Path>) stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))::iterator) {
                scanned++;
                try {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    QuestSpec spec = QuestSpecJsonReader.read(json);
                    if (spec == null) {
                        errors.add(new QuestDatapackLoadError(file, "Parsed quest spec is null", null));
                    } else {
                        specs.put(file.toAbsolutePath().normalize(), spec);
                    }
                } catch (Exception ex) {
                    errors.add(new QuestDatapackLoadError(file, "Failed to load quest spec", ex));
                }
            }
        } catch (IOException e) {
            errors.add(new QuestDatapackLoadError(questsDir, "Failed to scan quests directory", e));
        }

        return new LoadReport(specs, errors, scanned);
    }
}
