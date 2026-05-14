package org.arcadia.arc_quest.npc.io;

import org.arcadia.arc_quest.npc.spec.NpcSpec;
import org.arcadia.arc_quest.npc.spec.io.NpcSpecJsonReader;
import org.arcadia.arc_quest.quest.spec.io.DatapackPathResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NpcDatapackResourceLoader {

    public LoadReport loadFromDatapack() {
        Path npcDir = DatapackPathResolver.resolveNpcDir();
        Map<Path, NpcSpec> specs = new LinkedHashMap<>();
        List<NpcDatapackLoadError> errors = new ArrayList<>();

        if (!Files.exists(npcDir)) {
            return new LoadReport(specs, errors, 0);
        }

        int scanned = 0;
        try (var stream = Files.walk(npcDir)) {
            for (Path file : (Iterable<Path>) stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))::iterator) {
                scanned++;
                try {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    NpcSpec spec = NpcSpecJsonReader.read(json);
                    if (spec == null) {
                        errors.add(new NpcDatapackLoadError(file, "Parsed npc spec is null", null));
                    } else {
                        specs.put(file.toAbsolutePath().normalize(), spec);
                    }
                } catch (Exception ex) {
                    errors.add(new NpcDatapackLoadError(file, "Failed to load npc spec", ex));
                }
            }
        } catch (IOException e) {
            errors.add(new NpcDatapackLoadError(npcDir, "Failed to scan npc directory", e));
        }

        return new LoadReport(specs, errors, scanned);
    }

    public record LoadReport(Map<Path, NpcSpec> specs, List<NpcDatapackLoadError> errors, int scannedFiles) {
        public int loadedCount() {
            return specs.size();
        }

        public int failedCount() {
            return errors.size();
        }
    }

    public record NpcDatapackLoadError(Path file, String message, Exception exception) {
    }
}