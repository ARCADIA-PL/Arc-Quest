package org.arcadia.arc_quest.trade.io;

import org.arcadia.arc_quest.quest.spec.io.DatapackPathResolver;
import org.arcadia.arc_quest.trade.spec.TradeShopSpec;
import org.arcadia.arc_quest.trade.spec.io.TradeSpecJsonReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TradeDatapackResourceLoader {

    public LoadReport loadFromDatapack() {
        Path tradesDir = DatapackPathResolver.resolveTradesDir();
        Map<Path, TradeShopSpec> specs = new LinkedHashMap<>();
        List<TradeDatapackLoadError> errors = new ArrayList<>();

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

                    if (isGachaJson(json)) continue;

                    TradeShopSpec spec = TradeSpecJsonReader.read(json);
                    if (spec == null) {
                        errors.add(new TradeDatapackLoadError(file, "Parsed trade spec is null", null));
                    } else {
                        specs.put(file.toAbsolutePath().normalize(), spec);
                    }
                } catch (Exception ex) {
                    errors.add(new TradeDatapackLoadError(file, "Failed to load trade spec", ex));
                }
            }
        } catch (IOException e) {
            errors.add(new TradeDatapackLoadError(tradesDir, "Failed to scan trades directory", e));
        }

        return new LoadReport(specs, errors, scanned);
    }

    private static boolean isGachaJson(String json) {
        return json.contains("\"pools\"")
                && (json.contains("\"drawCost\"") || json.contains("\"drawCosts\""));
    }

    public record LoadReport(Map<Path, TradeShopSpec> specs, List<TradeDatapackLoadError> errors, int scannedFiles) {
        public int loadedCount() {
            return specs.size();
        }

        public int failedCount() {
            return errors.size();
        }
    }

    public record TradeDatapackLoadError(Path file, String message, Exception exception) {
    }
}
