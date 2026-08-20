package org.arcadia.arc_quest.data.sync;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClientQuestSnapshotProjector {
    private ClientQuestSnapshotProjector() {
    }

    public static List<ClientObjectiveTypeDescriptor> projectObjectiveTypes(
            Map<ResourceLocation, QuestDefinition> definitions) {
        Map<ResourceLocation, ClientObjectiveTypeDescriptor> descriptors = new LinkedHashMap<>();
        definitions.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            for (PhaseDefinition phase : entry.getValue().getAllPhases()) {
                for (ObjectiveEntry objective : phase.getObjectives()) {
                    descriptors.putIfAbsent(objective.getType().getId(),
                            ClientObjectiveTypeDescriptor.from(objective.getType()));
                }
            }
        });
        return List.copyOf(descriptors.values());
    }
}
