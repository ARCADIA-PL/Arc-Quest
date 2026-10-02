package org.arcadia.arc_quest.quest.tracking;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.ObjectiveItemResolver;
import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

public class ObjectiveTypeIndex {

    public record ObjectiveRef(ResourceLocation questId, String phaseId, int objIndex) {}

    private static final ObjectiveTypeIndex EMPTY = new ObjectiveTypeIndex();

    private final Map<ObjectiveType, Map<ResourceLocation, List<ObjectiveRef>>> byType
            = new HashMap<>();
    private final Map<ObjectiveType, List<TaggedObjectiveRef>> byTag = new HashMap<>();
    private record TaggedObjectiveRef(ObjectiveEntry objective, ObjectiveRef ref) {}

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
                    ObjectiveRef ref = new ObjectiveRef(def.getId(), phase.getPhaseId(), i);
                    if (ObjectiveItemResolver.isItemObjective(obj) && obj.hasTargetTag()) {
                        index.byTag.computeIfAbsent(obj.getType(), ignored -> new ArrayList<>())
                                .add(new TaggedObjectiveRef(obj, ref));
                    } else index.add(obj.getType(), target, ref);
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
        return find(type, targetId, objective -> ObjectiveItemResolver.matches(objective, targetId));
    }

    @Nullable
    public List<ObjectiveRef> find(ObjectiveType type, ResourceLocation targetId, Predicate<ObjectiveEntry> taggedMatch) {
        Map<ResourceLocation, List<ObjectiveRef>> targets = byType.get(type);
        List<ObjectiveRef> result = new ArrayList<>();
        if (targets != null) result.addAll(targets.getOrDefault(targetId, List.of()));
        // Resolve against live tags: registry construction can precede tag loading/reloading.
        for (TaggedObjectiveRef tagged : byTag.getOrDefault(type, List.of())) {
            if (taggedMatch.test(tagged.objective())) result.add(tagged.ref());
        }
        return result.isEmpty() ? null : List.copyOf(result);
    }
}
