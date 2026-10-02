package org.arcadia.arc_quest.quest.api;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Shared item/tag semantics for objective tracking, native presentation and optional integrations. */
public final class ObjectiveItemResolver {
    public static final String FROZEN_TAG_MEMBERS = "frozen_tag_members";
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
            if (objective.getExtraData().containsKey(FROZEN_TAG_MEMBERS)) {
                for (ResourceLocation id : frozenTagMembers(objective)) {
                    Item item = ForgeRegistries.ITEMS.getValue(id);
                    if (item != null) {
                        ItemStack stack = new ItemStack(item);
                        if (!stack.isEmpty()) result.add(stack);
                    }
                }
                result.sort(Comparator.comparing(stack -> ForgeRegistries.ITEMS.getKey(stack.getItem()).toString()));
                return List.copyOf(result);
            }
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

    public static String encodeFrozenTagMembers(java.util.Collection<ResourceLocation> ids) {
        return ids.stream().map(ResourceLocation::toString).sorted().collect(java.util.stream.Collectors.joining(","));
    }

    private static List<ResourceLocation> frozenTagMembers(ObjectiveEntry objective) {
        String value = objective.getExtraData().get(FROZEN_TAG_MEMBERS);
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split(",")).map(ResourceLocation::tryParse)
                .filter(java.util.Objects::nonNull).filter(ForgeRegistries.ITEMS::containsKey).distinct().toList();
    }

    public static boolean matches(ObjectiveEntry objective, ResourceLocation itemId) {
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) return false;
        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        return item != null && matches(objective, new ItemStack(item));
    }

    /** An accepted investigation's Tag members are authoritative even after a data-pack reload. */
    public static boolean matches(ObjectiveEntry objective, ResourceLocation itemId, QuestRuntimeData runtime) {
        if (itemId == null || !ForgeRegistries.ITEMS.containsKey(itemId) || !isItemObjective(objective)) return false;
        if (objective.hasTargetTag() && runtime != null && runtime.hasFrozenItemTag(objective.getTargetTagResourceLocation()))
            return runtime.getFrozenItemTagMembers(objective.getTargetTagResourceLocation()).contains(itemId);
        return matches(objective, itemId);
    }

    public static boolean matches(ObjectiveEntry objective, ItemStack stack, QuestRuntimeData runtime) {
        return stack != null && !stack.isEmpty()
                && matches(objective, ForgeRegistries.ITEMS.getKey(stack.getItem()), runtime);
    }

    /** These objective kinds match item identity/tags, never a decorative icon or stack NBT. */
    public static boolean matches(ObjectiveEntry objective, ItemStack stack) {
        if (!isItemObjective(objective) || stack == null || stack.isEmpty()) return false;
        if (objective.hasTargetTag()) {
            if (objective.getExtraData().containsKey(FROZEN_TAG_MEMBERS))
                return frozenTagMembers(objective).contains(ForgeRegistries.ITEMS.getKey(stack.getItem()));
            ResourceLocation tag = objective.getTargetTagResourceLocation();
            return tag != null && stack.is(TagKey.create(Registries.ITEM, tag));
        }
        return objective.getTargetId().equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }
}
