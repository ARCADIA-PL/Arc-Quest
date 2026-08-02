package org.arcadia.arc_quest.data.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record LayeredRegistrySnapshot<K, V>(
        Map<K, V> datapack,
        Map<K, V> merged,
        Map<K, RegistrySourceInfo> sources
) {
    public static <K, V> LayeredRegistrySnapshot<K, V> create(Map<K, V> code, Map<K, V> datapack) {
        Map<K, V> immutableDatapack = Collections.unmodifiableMap(new LinkedHashMap<>(datapack));
        Map<K, V> merged = new LinkedHashMap<>(immutableDatapack);
        Map<K, RegistrySourceInfo> sources = new LinkedHashMap<>();
        int loadOrder = 0;
        for (K id : immutableDatapack.keySet()) {
            sources.put(id, new RegistrySourceInfo(RegistrySourceType.DATAPACK, id.toString(), loadOrder++, null));
        }
        for (Map.Entry<K, V> entry : code.entrySet()) {
            boolean overridesDatapack = merged.containsKey(entry.getKey());
            sources.put(entry.getKey(), new RegistrySourceInfo(
                    RegistrySourceType.CODE,
                    "code",
                    loadOrder++,
                    overridesDatapack ? "datapack_ignored_due_to_code_priority" : null));
            merged.put(entry.getKey(), entry.getValue());
        }
        return new LayeredRegistrySnapshot<>(
                immutableDatapack,
                Collections.unmodifiableMap(merged),
                Collections.unmodifiableMap(sources));
    }

    public static <K, V> LayeredRegistrySnapshot<K, V> empty(Map<K, V> code) {
        return create(code, Map.of());
    }
}
