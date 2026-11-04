package org.arcadia.arc_quest.quest.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.arcadia.arc_quest.quest.api.*;

import java.util.*;
import java.util.function.Function;

/** Common extension point. Addons own third-party parsing; preview stacks never enter inventory. */
public final class CollectionSubjectRegistry {
    private static final Map<ResourceLocation, Function<CollectionSubjectSpec, ItemStack>>
            RESOLVERS = new HashMap<>();

    private static final Map<CollectionSubjectSpec, ItemStack> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private CollectionSubjectRegistry() {}

    public static void invalidate() {
        CACHE.clear();
    }

    public static synchronized void register(
            ResourceLocation type, Function<CollectionSubjectSpec, ItemStack> resolver) {
        if (RESOLVERS.putIfAbsent(type, Objects.requireNonNull(resolver)) != null)
            throw new IllegalArgumentException("Duplicate subject resolver: " + type);
    }

    public static ItemStack resolve(CollectionSubjectSpec s) {
        ItemStack cached = CACHE.get(s);
        if (cached != null) return cached.copy();
        ItemStack result;
        if (s.type().equals(ResourceLocation.parse("arc_quest:item_stack"))) {
            if (!BuiltInRegistries.ITEM.containsKey(s.id())) return ItemStack.EMPTY;
            result = new ItemStack(BuiltInRegistries.ITEM.get(s.id()), s.count());
            if (!s.snbt().isBlank()) result.setTag(StackMatchSpec.parse(s.snbt()));
        } else {
            var resolver = RESOLVERS.get(s.type());
            result = resolver == null ? ItemStack.EMPTY : resolver.apply(s);
        }
        if (result == null || result.isEmpty()) return ItemStack.EMPTY;
        if (!s.identity().matches(result, result))
            throw new IllegalArgumentException(
                    "Subject identity does not match preview: " + s.id());
        CACHE.put(s, result.copy());
        return result.copy();
    }
}
