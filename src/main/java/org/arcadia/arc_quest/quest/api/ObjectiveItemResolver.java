package org.arcadia.arc_quest.quest.api;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

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
            var tags = ForgeRegistries.ITEMS.tags();
            if (tag == null || tags == null) return List.of();
            for (Item item : tags.getTag(TagKey.create(Registries.ITEM, tag))) {
                ItemStack stack = new ItemStack(item);
                if (!stack.isEmpty()) result.add(stack);
            }
        } else {
            ResourceLocation id = objective.getTargetId();
            if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) return List.of();
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item != null) {
                ItemStack stack = new ItemStack(item);
                if (!stack.isEmpty()) result.add(stack);
            }
        }
        result.sort(Comparator.comparing(stack -> ForgeRegistries.ITEMS.getKey(stack.getItem()).toString()));
        return List.copyOf(result);
    }

    public static List<ResourceLocation> targetIds(ObjectiveEntry objective) {
        return candidates(objective).stream().map(stack -> ForgeRegistries.ITEMS.getKey(stack.getItem())).toList();
    }

    public static boolean matches(ObjectiveEntry objective, ResourceLocation itemId) {
        if (itemId == null || !ForgeRegistries.ITEMS.containsKey(itemId)) return false;
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        return item != null && matches(objective, new ItemStack(item));
    }

    /** These objective kinds match item identity/tags, never a decorative icon or stack NBT. */
    public static boolean matches(ObjectiveEntry objective, ItemStack stack) {
        if (!isItemObjective(objective) || stack == null || stack.isEmpty()) return false;
        if (objective.hasTargetTag()) {
            ResourceLocation tag = objective.getTargetTagResourceLocation();
            return tag != null && stack.is(TagKey.create(Registries.ITEM, tag));
        }
        return objective.getTargetId().equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }
}
