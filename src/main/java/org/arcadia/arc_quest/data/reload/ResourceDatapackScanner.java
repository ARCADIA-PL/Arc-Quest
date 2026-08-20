package org.arcadia.arc_quest.data.reload;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class ResourceDatapackScanner {
    private ResourceDatapackScanner() {
    }

    static boolean isResourcePath(Path source) {
        return source != null && source.getNameCount() > 0
                && "__resources__".equals(source.getName(0).toString());
    }

    static <T> SafeDatapackScanner.ScanResult<T> scan(String module, ResourceManager manager, String prefix,
                                                       ReloadLimits limits, SafeDatapackScanner.JsonDecoder<T> decoder) {
        ScanState<T> state = new ScanState<>();
        Map<ResourceLocation, Resource> resources = new TreeMap<>(manager.listResources(prefix,
                location -> location.getPath().endsWith(".json")));
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            if (!scanEntry(module, entry, limits, decoder, state)) break;
        }
        return state.result();
    }

    private static <T> boolean scanEntry(String module, Map.Entry<ResourceLocation, Resource> entry,
                                         ReloadLimits limits, SafeDatapackScanner.JsonDecoder<T> decoder,
                                         ScanState<T> state) {
        Path source = resourcePath(entry.getKey(), entry.getValue());
        state.files.add(source);
        if (state.files.size() > limits.maxFiles()) {
            state.error(module, null, "File count exceeds limit " + limits.maxFiles(), null);
            return false;
        }
        return readEntry(module, source, entry.getValue(), limits, decoder, state);
    }

    private static <T> boolean readEntry(String module, Path source, Resource resource, ReloadLimits limits,
                                         SafeDatapackScanner.JsonDecoder<T> decoder, ScanState<T> state) {
        try (InputStream input = resource.open()) {
            byte[] bytes = input.readAllBytes();
            if (bytes.length > limits.maxFileBytes()) {
                state.error(module, source, "File size exceeds limit " + limits.maxFileBytes(), null);
                return true;
            }
            if (state.parsedBytes + bytes.length > limits.maxParsedBytes()) {
                state.error(module, source, "Total parsed bytes exceed limit " + limits.maxParsedBytes(), null);
                return false;
            }
            T value = decode(bytes, limits, decoder);
            if (value == null) throw new IllegalArgumentException("Parsed value is null");
            state.values.put(source, value);
            state.parsedBytes += bytes.length;
        } catch (SafeDatapackScanner.SkipFileException ignored) {
        } catch (Exception exception) {
            state.error(module, source, "Failed to decode resource: " + exception.getMessage(), exception);
        }
        return true;
    }

    private static <T> T decode(byte[] bytes, ReloadLimits limits,
                                SafeDatapackScanner.JsonDecoder<T> decoder) throws Exception {
        String json = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        JsonElement tree = JsonParser.parseString(json);
        if (countCollectionElements(tree, limits.maxCollectionElements()) > limits.maxCollectionElements()) {
            throw new IllegalArgumentException("JSON collection elements exceed limit " + limits.maxCollectionElements());
        }
        return decoder.decode(json, tree);
    }

    private static int countCollectionElements(JsonElement element, int limit) {
        if (element == null || element.isJsonNull() || element.isJsonPrimitive()) return 0;
        int count = element.isJsonArray() ? element.getAsJsonArray().size() : element.getAsJsonObject().size();
        Iterable<JsonElement> children = element.isJsonArray()
                ? element.getAsJsonArray()
                : element.getAsJsonObject().entrySet().stream().map(Map.Entry::getValue).toList();
        for (JsonElement child : children) {
            count += countCollectionElements(child, limit - count);
            if (count > limit) return count;
        }
        return count;
    }

    private static Path resourcePath(ResourceLocation location, Resource resource) {
        String pack = resource.sourcePackId().replaceAll("[^A-Za-z0-9._-]", "_");
        return Path.of("__resources__", pack, location.getNamespace(), location.getPath());
    }

    private static ResourceLocation resourceLocation(Path source, String prefix) {
        if (source.getNameCount() < 4) return null;
        int namespaceIndex = 2;
        String namespace = source.getName(namespaceIndex).toString();
        StringBuilder path = new StringBuilder();
        for (int index = namespaceIndex + 1; index < source.getNameCount(); index++) {
            if (!path.isEmpty()) path.append('/');
            path.append(source.getName(index));
        }
        return path.toString().startsWith(prefix + "/")
                ? ResourceLocation.tryParse(namespace + ":" + path)
                : null;
    }

    private static ResourceLocation externalLocation(Path root, Path source, String prefix) {
        Path relative = root.toAbsolutePath().normalize().relativize(source.toAbsolutePath().normalize());
        String namespace = "arc_quest";
        int start = 0;
        if (relative.getNameCount() > 1 && ResourceLocation.isValidNamespace(relative.getName(0).toString())) {
            namespace = relative.getName(0).toString();
            start = 1;
        }
        StringBuilder path = new StringBuilder(prefix);
        for (int index = start; index < relative.getNameCount(); index++) {
            path.append('/').append(relative.getName(index));
        }
        return ResourceLocation.fromNamespaceAndPath(namespace, path.toString().replace('\\', '/'));
    }

    static <T> SafeDatapackScanner.ScanResult<T> merge(String module, Path root, String prefix,
                                                        ReloadLimits limits,
                                                        SafeDatapackScanner.ScanResult<T> resources,
                                                        SafeDatapackScanner.ScanResult<T> external) {
        Map<Path, T> values = new LinkedHashMap<>(resources.values());
        Map<ResourceLocation, Path> resourceSources = new LinkedHashMap<>();
        resources.values().keySet().forEach(source -> {
            ResourceLocation id = resourceLocation(source, prefix);
            if (id != null) resourceSources.put(id, source);
        });
        external.values().forEach((source, value) -> {
            Path replaced = resourceSources.remove(externalLocation(root, source, prefix));
            if (replaced != null) values.remove(replaced);
            values.put(source, value);
        });
        Set<Path> files = new LinkedHashSet<>(resources.scannedFiles());
        files.addAll(external.scannedFiles());
        List<ReloadDiagnostic> diagnostics = new ArrayList<>(resources.diagnostics());
        diagnostics.addAll(external.diagnostics());
        long bytes = resources.parsedBytes() + external.parsedBytes();
        if (files.size() > limits.maxFiles() || bytes > limits.maxParsedBytes()) {
            diagnostics.add(ReloadDiagnostic.error(module, null, "$",
                    "Combined resource and override limits exceeded"));
        }
        return new SafeDatapackScanner.ScanResult<>(values, files, bytes, diagnostics, external.fingerprints());
    }

    private static final class ScanState<T> {
        private final Map<Path, T> values = new LinkedHashMap<>();
        private final Set<Path> files = new LinkedHashSet<>();
        private final List<ReloadDiagnostic> diagnostics = new ArrayList<>();
        private long parsedBytes;

        private void error(String module, Path source, String message, Throwable cause) {
            diagnostics.add(cause == null
                    ? ReloadDiagnostic.error(module, source, "$", message)
                    : ReloadDiagnostic.error(module, source, "$", message, cause));
        }

        private SafeDatapackScanner.ScanResult<T> result() {
            return new SafeDatapackScanner.ScanResult<>(values, files, parsedBytes, diagnostics, Map.of());
        }
    }
}
