package org.arcadia.arc_quest.quest.tracking;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ObjectiveTypeIndex {

    public record ObjectiveRef(ResourceLocation questId, String phaseId, int objIndex) {}

    private static final ObjectiveTypeIndex EMPTY = new ObjectiveTypeIndex();

    private final Map<ObjectiveType, Map<ResourceLocation, List<ObjectiveRef>>> byType
            = new EnumMap<>(ObjectiveType.class);

    private ObjectiveTypeIndex() {}

    public static ObjectiveTypeIndex build(Map<ResourceLocation, QuestDefinition> registry) {
        ObjectiveTypeIndex index = new ObjectiveTypeIndex();
        for (QuestDefinition def : registry.values()) {
            for (PhaseDefinition phase : def.getAllPhases()) {
                List<ObjectiveEntry> objectives = phase.getObjectives();
                for (int i = 0; i < objectives.size(); i++) {
                    ObjectiveEntry obj = objectives.get(i);
                    ResourceLocation target = obj.getTargetId();
                    if (target == null) continue;
                    index.add(obj.getType(), target,
                            new ObjectiveRef(def.getId(), phase.getPhaseId(), i));
                }
            }
        }
        return index;
    }

    private void add(ObjectiveType type, ResourceLocation target, ObjectiveRef ref) {
        byType.computeIfAbsent(type, k -> new HashMap<>())
                .computeIfAbsent(target, k -> new ArrayList<>())
                .add(ref);
    }

    public static ObjectiveTypeIndex empty() {
        return EMPTY;
    }

    @Nullable
    public List<ObjectiveRef> find(ObjectiveType type, ResourceLocation targetId) {
        Map<ResourceLocation, List<ObjectiveRef>> targets = byType.get(type);
        return targets != null ? targets.get(targetId) : null;
    }
}
