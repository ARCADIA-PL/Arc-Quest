package org.arcadia.arc_quest.quest.api;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Shared item/tag semantics for objective tracking, native presentation and optional integrations. */
public final class ObjectiveItemResolver {
    private ObjectiveItemResolver() {}

    public static boolean isItemObjective(ObjectiveEntry objective) {
        if (objective == null) return false;
        ObjectiveType type = objective.getType();
        return ObjectiveType.COLLECT.equals(type) || ObjectiveType.CRAFT.equals(type)
                || ObjectiveType.OFFER.equals(type) || ObjectiveType.DELIVER.equals(type);
    }

    /** Fresh count-one copies; an empty, missing or invalid tag never falls back to targetId. */
    public static List<ItemStack> candidates(ObjectiveEntry objective) {
        if (!isItemObjective(objective)) return List.of();
        List<ItemStack> result = new ArrayList<>();
        if (objective.hasTargetTag()) {
            ResourceLocation tag = objective.getTargetTagResourceLocation();
            if (tag == null) return List.of();
            var items = BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, tag)).orElse(null);
            if (items == null) return List.of();
            for (var item : items) {
                ItemStack stack = new ItemStack(item.value());
                if (!stack.isEmpty()) result.add(stack);
            }
        } else {
            ResourceLocation id = objective.getTargetId();
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return List.of();
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item != null) {
                ItemStack stack = new ItemStack(item);
                if (!stack.isEmpty()) result.add(stack);
            }
        }
        result.sort(Comparator.comparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
        return List.copyOf(result);
    }

    public static List<ResourceLocation> targetIds(ObjectiveEntry objective) {
        return candidates(objective).stream().map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem())).toList();
    }

    public static boolean matches(ObjectiveEntry objective, ResourceLocation itemId) {
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) return false;
        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        return item != null && matches(objective, new ItemStack(item));
    }

    /** These objective kinds match item identity/tags, never a decorative icon or stack NBT. */
    public static boolean matches(ObjectiveEntry objective, ItemStack stack) {
        if (!isItemObjective(objective) || stack == null || stack.isEmpty()) return false;
        if (objective.hasTargetTag()) {
            ResourceLocation tag = objective.getTargetTagResourceLocation();
            return tag != null && stack.is(TagKey.create(Registries.ITEM, tag));
        }
        return objective.getTargetId().equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }
}
