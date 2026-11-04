package org.arcadia.arc_quest.quest.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.arcadia.arc_quest.quest.registry.CollectionSubjectRegistry;

import java.util.Map;

public record CollectionSubjectSpec(
        ResourceLocation type,
        ResourceLocation id,
        int count,
        String snbt,
        StackMatchSpec identity,
        Map<String, String> parameters) {
    public CollectionSubjectSpec {
        if (type == null || id == null)
            throw new IllegalArgumentException("Subject type/id required");
        if (count < 1 || count > 64)
            throw new IllegalArgumentException("Subject count must be 1..64");
        snbt = snbt == null ? "" : snbt;
        StackMatchSpec.parse(snbt);
        identity = identity == null ? new StackMatchSpec(StackMatchSpec.Mode.ITEM, "") : identity;
        parameters = Map.copyOf(parameters == null ? Map.of() : parameters);
    }

    public ItemStack resolveStack() {
        return CollectionSubjectRegistry.resolve(this);
    }

    public static CollectionSubjectSpec item(ResourceLocation id) {
        return new CollectionSubjectSpec(
                ResourceLocation.parse("arc_quest:item_stack"), id, 1, "", null, Map.of());
    }
}
