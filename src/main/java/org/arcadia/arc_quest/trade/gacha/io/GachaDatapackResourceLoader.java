package org.arcadia.arc_quest.trade.gacha.io;

import org.arcadia.arc_quest.quest.spec.io.DatapackPathResolver;
import org.arcadia.arc_quest.trade.gacha.spec.GachaShopSpec;
import org.arcadia.arc_quest.trade.gacha.spec.io.GachaSpecJsonReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GachaDatapackResourceLoader {

    public LoadReport loadFromDatapack() {
        Path tradesDir = DatapackPathResolver.resolveTradesDir();
        Map<Path, GachaShopSpec> specs = new LinkedHashMap<>();
        List<GachaDatapackLoadError> errors = new ArrayList<>();

        if (!Files.exists(tradesDir)) {
            return new LoadReport(specs, errors, 0);
        }

        int scanned = 0;
        try (var stream = Files.walk(tradesDir)) {
            for (Path file : (Iterable<Path>) stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))::iterator) {
                scanned++;
                try {
                    String json = Files.readString(file, StandardCharsets.UTF_8);

                    if (isTradeJson(json)) continue;

                    GachaShopSpec spec = GachaSpecJsonReader.read(json);
                    if (spec == null) {
                        errors.add(new GachaDatapackLoadError(file, "Parsed gacha spec is null", null));
                        continue;
                    }
                    boolean hasDrawCost = spec.drawCost != null
                            || (spec.drawCosts != null && !spec.drawCosts.isEmpty());
                    if (!hasDrawCost || spec.pools == null || spec.pools.isEmpty()) {
                        continue;
                    }
                    specs.put(file.toAbsolutePath().normalize(), spec);
                } catch (Exception ex) {
                    errors.add(new GachaDatapackLoadError(file, "Failed to load gacha spec", ex));
                }
            }
        } catch (IOException e) {
            errors.add(new GachaDatapackLoadError(tradesDir, "Failed to scan trades directory", e));
        }

        return new LoadReport(specs, errors, scanned);
    }

    private static boolean isTradeJson(String json) {
        return json.contains("\"entries\"") && json.contains("\"shopId\"");
    }

    public record LoadReport(Map<Path, GachaShopSpec> specs, List<GachaDatapackLoadError> errors, int scannedFiles) {
        public int loadedCount() {
            return specs.size();
        }

        public int failedCount() {
            return errors.size();
        }
    }

    public record GachaDatapackLoadError(Path file, String message, Exception exception) {
    }
}
