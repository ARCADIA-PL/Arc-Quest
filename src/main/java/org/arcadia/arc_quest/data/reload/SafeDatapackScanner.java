package org.arcadia.arc_quest.data.reload;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SafeDatapackScanner {
    private final ReloadLimits limits;
    private final FileFingerprintProvider fingerprintProvider;

    public SafeDatapackScanner(ReloadLimits limits) {
        this(limits, FileFingerprintProvider.disabled());
    }

    public SafeDatapackScanner(ReloadLimits limits, FileFingerprintProvider fingerprintProvider) {
        this.limits = limits;
        this.fingerprintProvider = fingerprintProvider;
    }

    public <T> ScanResult<T> scan(String module, Path root, JsonDecoder<T> decoder) {
        Map<Path, T> values = new LinkedHashMap<>();
        Set<Path> files = new LinkedHashSet<>();
        List<ReloadDiagnostic> diagnostics = new ArrayList<>();
        Map<Path, String> fingerprints = new LinkedHashMap<>();
        long parsedBytes = 0L;
        if (!Files.exists(root)) return new ScanResult<>(values, files, parsedBytes, diagnostics, fingerprints);

        try (var stream = Files.walk(root, limits.maxDirectoryDepth() + 1)) {
            for (Path file : (Iterable<Path>) stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))::iterator) {
                Path normalized = file.toAbsolutePath().normalize();
                files.add(normalized);
                int relativeDepth = root.toAbsolutePath().normalize().relativize(normalized).getNameCount();
                if (relativeDepth > limits.maxDirectoryDepth()) {
                    diagnostics.add(ReloadDiagnostic.error(module, normalized, "$",
                            "Directory depth " + relativeDepth + " exceeds limit " + limits.maxDirectoryDepth()));
                    continue;
                }
                if (files.size() > limits.maxFiles()) {
                    diagnostics.add(ReloadDiagnostic.error(module, null, "$",
                            "File count exceeds limit " + limits.maxFiles()));
                    break;
                }
                long size = Files.size(normalized);
                if (size > limits.maxFileBytes()) {
                    diagnostics.add(ReloadDiagnostic.error(module, normalized, "$",
                            "File size " + size + " exceeds limit " + limits.maxFileBytes()));
                    continue;
                }
                if (parsedBytes + size > limits.maxParsedBytes()) {
                    diagnostics.add(ReloadDiagnostic.error(module, null, "$",
                            "Total parsed bytes exceed limit " + limits.maxParsedBytes()));
                    break;
                }
                try {
                    byte[] bytes = Files.readAllBytes(normalized);
                    fingerprintProvider.fingerprint(normalized, bytes).ifPresent(value -> fingerprints.put(normalized, value));
                    String json = StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes)).toString();
                    JsonElement tree = JsonParser.parseString(json);
                    if (countCollectionElements(tree, limits.maxCollectionElements()) > limits.maxCollectionElements()) {
                        diagnostics.add(ReloadDiagnostic.error(module, normalized, "$",
                                "JSON collection elements exceed limit " + limits.maxCollectionElements()));
                        continue;
                    }
                    T value = decoder.decode(json, tree);
                    if (value == null) {
                        diagnostics.add(ReloadDiagnostic.error(module, normalized, "$", "Parsed value is null"));
                        continue;
                    }
                    values.put(normalized, value);
                    parsedBytes += size;
                } catch (SkipFileException ignored) {
                } catch (Exception exception) {
                    diagnostics.add(ReloadDiagnostic.error(module, normalized, "$",
                            "Failed to decode or parse JSON: " + exception.getMessage(), exception));
                }
            }
        } catch (IOException exception) {
            diagnostics.add(ReloadDiagnostic.error(module, null, "$",
                    "Failed to scan directory: " + exception.getMessage(), exception));
        }
        return new ScanResult<>(values, files, parsedBytes, diagnostics, fingerprints);
    }

    private static int countCollectionElements(JsonElement element, int limit) {
        if (element == null || element.isJsonNull() || element.isJsonPrimitive()) return 0;
        int count = 0;
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            count += array.size();
            for (JsonElement child : array) {
                count += countCollectionElements(child, limit - count);
                if (count > limit) return count;
            }
            return count;
        }
        JsonObject object = element.getAsJsonObject();
        count += object.size();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            count += countCollectionElements(entry.getValue(), limit - count);
            if (count > limit) return count;
        }
        return count;
    }

    @FunctionalInterface
    public interface JsonDecoder<T> {
        T decode(String json, JsonElement parsedTree) throws Exception;
    }

    public static final class SkipFileException extends Exception {
        public static final SkipFileException INSTANCE = new SkipFileException();

        private SkipFileException() {
        }
    }

    @FunctionalInterface
    public interface FileFingerprintProvider {
        Optional<String> fingerprint(Path file, byte[] content);

        static FileFingerprintProvider disabled() {
            return (file, content) -> Optional.empty();
        }
    }

    public record ScanResult<T>(Map<Path, T> values, Set<Path> scannedFiles, long parsedBytes,
                                List<ReloadDiagnostic> diagnostics, Map<Path, String> fingerprints) {
    }
}
