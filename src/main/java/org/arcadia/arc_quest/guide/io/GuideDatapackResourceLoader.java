package org.arcadia.arc_quest.guide.io;

import org.arcadia.arc_quest.guide.spec.GuideCategorySpec;
import org.arcadia.arc_quest.guide.spec.GuideSpec;
import org.arcadia.arc_quest.guide.spec.io.GuideCategorySpecJsonReader;
import org.arcadia.arc_quest.guide.spec.io.GuideSpecJsonReader;
import org.arcadia.arc_quest.quest.spec.io.DatapackPathResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GuideDatapackResourceLoader {

    public LoadReport loadFromDatapack() {
        Path root = DatapackPathResolver.resolveDatapackRoot();
        Path categoriesDir = root.resolve("guide_categories").normalize();
        Path guidesDir = root.resolve("guides").normalize();
        Map<Path, GuideCategorySpec> categories = new LinkedHashMap<>();
        Map<Path, GuideSpec> guides = new LinkedHashMap<>();
        List<GuideDatapackLoadError> errors = new ArrayList<>();

        int categoryScanned = scanCategories(categoriesDir, categories, errors);
        int guideScanned = scanGuides(guidesDir, guides, errors);
        return new LoadReport(categories, guides, errors, categoryScanned, guideScanned);
    }

    private int scanCategories(Path dir, Map<Path, GuideCategorySpec> categories, List<GuideDatapackLoadError> errors) {
        if (!Files.exists(dir)) {
            return 0;
        }
        int scanned = 0;
        try (var stream = Files.walk(dir)) {
            for (Path file : (Iterable<Path>) stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))::iterator) {
                scanned++;
                try {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    categories.put(file.toAbsolutePath().normalize(), GuideCategorySpecJsonReader.read(json));
                } catch (Exception ex) {
                    errors.add(new GuideDatapackLoadError(file, "Failed to load guide category spec", ex));
                }
            }
        } catch (IOException ex) {
            errors.add(new GuideDatapackLoadError(dir, "Failed to scan guide category directory", ex));
        }
        return scanned;
    }

    private int scanGuides(Path dir, Map<Path, GuideSpec> guides, List<GuideDatapackLoadError> errors) {
        if (!Files.exists(dir)) {
            return 0;
        }
        int scanned = 0;
        try (var stream = Files.walk(dir)) {
            for (Path file : (Iterable<Path>) stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))::iterator) {
                scanned++;
                try {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    guides.put(file.toAbsolutePath().normalize(), GuideSpecJsonReader.read(json));
                } catch (Exception ex) {
                    errors.add(new GuideDatapackLoadError(file, "Failed to load guide spec", ex));
                }
            }
        } catch (IOException ex) {
            errors.add(new GuideDatapackLoadError(dir, "Failed to scan guide directory", ex));
        }
        return scanned;
    }

    public record LoadReport(
            Map<Path, GuideCategorySpec> categories,
            Map<Path, GuideSpec> guides,
            List<GuideDatapackLoadError> errors,
            int categoryScannedFiles,
            int guideScannedFiles
    ) {
        public int failedCount() {
            return errors.size();
        }
    }
}
