package org.arcadia.arc_quest.dialogue.io;

import org.arcadia.arc_quest.dialogue.spec.DialogueSpec;
import org.arcadia.arc_quest.dialogue.spec.io.DialogueSpecJsonReader;
import org.arcadia.arc_quest.quest.spec.io.DatapackPathResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DialogueDatapackResourceLoader {

    public LoadReport loadFromDatapack() {
        Path dialoguesDir = DatapackPathResolver.resolveDialoguesDir();
        Map<Path, DialogueSpec> specs = new LinkedHashMap<>();
        List<DialogueDatapackLoadError> errors = new ArrayList<>();

        if (!Files.exists(dialoguesDir)) {
            return new LoadReport(specs, errors, 0);
        }

        int scanned = 0;
        try (var stream = Files.walk(dialoguesDir)) {
            for (Path file : (Iterable<Path>) stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))::iterator) {
                scanned++;
                try {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    DialogueSpec spec = DialogueSpecJsonReader.read(json);
                    if (spec == null) {
                        errors.add(new DialogueDatapackLoadError(file, "Parsed dialogue spec is null", null));
                    } else {
                        specs.put(file.toAbsolutePath().normalize(), spec);
                    }
                } catch (Exception ex) {
                    errors.add(new DialogueDatapackLoadError(file, "Failed to load dialogue spec", ex));
                }
            }
        } catch (IOException e) {
            errors.add(new DialogueDatapackLoadError(dialoguesDir, "Failed to scan dialogues directory", e));
        }

        return new LoadReport(specs, errors, scanned);
    }

    public record LoadReport(Map<Path, DialogueSpec> specs, List<DialogueDatapackLoadError> errors, int scannedFiles) {
        public int loadedCount() {
            return specs.size();
        }

        public int failedCount() {
            return errors.size();
        }
    }

    public record DialogueDatapackLoadError(Path file, String message, Exception exception) {
    }
}