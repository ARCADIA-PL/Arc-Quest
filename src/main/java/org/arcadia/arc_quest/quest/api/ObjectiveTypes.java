package org.arcadia.arc_quest.quest.api;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.registry.ObjectiveTypeRegistry;

public final class ObjectiveTypes {

    private ObjectiveTypes() {
    }

    public static final ObjectiveType NULL = registerBuiltin("null", false, false, "arc_quest.objective.null", "none");
    public static final ObjectiveType KILL = registerBuiltin("kill", true, true, "arc_quest.objective.kill", "entity_type");
    public static final ObjectiveType COLLECT = registerBuiltin("collect", true, true, "arc_quest.objective.collect", "item_or_tag");
    public static final ObjectiveType TALK = registerBuiltin("talk", false, true, "arc_quest.objective.talk", "npc");
    public static final ObjectiveType INTERACT = registerBuiltin("interact", false, true, "arc_quest.objective.interact", "block_or_entity");
    public static final ObjectiveType REACH_LOCATION = registerBuiltin("reach_location", false, true, "arc_quest.objective.reach_location", "location");
    public static final ObjectiveType DELIVER = registerBuiltin("deliver", true, true, "arc_quest.objective.deliver", "item_or_tag");
    public static final ObjectiveType CRAFT = registerBuiltin("craft", false, true, "arc_quest.objective.craft", "item");
    public static final ObjectiveType OFFER = registerBuiltin("offer", false, true, "arc_quest.objective.offer", "item_or_tag");
    public static final ObjectiveType CUSTOM = registerBuiltin("custom", false, true, "arc_quest.objective.custom", "custom");

    private static ObjectiveType registerBuiltin(String path,
                                                 boolean counting,
                                                 boolean requireTargetId,
                                                 String displayKey,
                                                 String defaultTargetKind) {
        return ObjectiveTypeRegistry.register(
                ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, path),
                ObjectiveTypeDefinition.builder()
                        .builtin(true)
                        .counting(counting)
                        .displayKey(displayKey)
                        .requireTargetId(requireTargetId)
                        .defaultTargetKind(defaultTargetKind)
                        .build()
        );
    }
}
