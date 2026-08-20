package org.arcadia.arc_quest.data.sync;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record DatapackContentSnapshot(long epoch, Map<DatapackContentModule, List<String>> documents,
                                      List<ClientObjectiveTypeDescriptor> objectiveTypes) {

    public DatapackContentSnapshot {
        EnumMap<DatapackContentModule, List<String>> copy = new EnumMap<>(DatapackContentModule.class);
        for (DatapackContentModule module : DatapackContentModule.values()) {
            copy.put(module, List.copyOf(documents.getOrDefault(module, List.of())));
        }
        documents = Map.copyOf(copy);
        objectiveTypes = List.copyOf(objectiveTypes == null ? List.of() : objectiveTypes);
    }

    public DatapackContentSnapshot(long epoch, Map<DatapackContentModule, List<String>> documents) {
        this(epoch, documents, List.of());
    }

    public static DatapackContentSnapshot empty(long epoch) {
        return new DatapackContentSnapshot(epoch, Map.of(), List.of());
    }

    public List<String> documents(DatapackContentModule module) {
        return documents.getOrDefault(module, List.of());
    }
}
