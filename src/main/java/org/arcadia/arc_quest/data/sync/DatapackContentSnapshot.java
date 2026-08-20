package org.arcadia.arc_quest.data.sync;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record DatapackContentSnapshot(long epoch, Map<DatapackContentModule, List<String>> documents) {

    public DatapackContentSnapshot {
        EnumMap<DatapackContentModule, List<String>> copy = new EnumMap<>(DatapackContentModule.class);
        for (DatapackContentModule module : DatapackContentModule.values()) {
            copy.put(module, List.copyOf(documents.getOrDefault(module, List.of())));
        }
        documents = Map.copyOf(copy);
    }

    public static DatapackContentSnapshot empty(long epoch) {
        return new DatapackContentSnapshot(epoch, Map.of());
    }

    public List<String> documents(DatapackContentModule module) {
        return documents.getOrDefault(module, List.of());
    }
}
