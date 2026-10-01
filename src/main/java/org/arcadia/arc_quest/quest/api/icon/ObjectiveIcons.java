package org.arcadia.arc_quest.quest.api.icon;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Author-facing factories for objective icon policies. Safe to use on dedicated servers. */
public final class ObjectiveIcons {
    private ObjectiveIcons() {}

    public static ObjectiveIconSpec auto() { return ObjectiveIconSpec.AUTO; }
    public static ObjectiveIconSpec none() { return ObjectiveIconSpec.NONE; }

    /** Selects the item's default inventory model; never changes the objective's real target. */
    public static ObjectiveIconSpec item(ItemLike item) {
        var registeredItem = Objects.requireNonNull(item, "icon.item must not be null").asItem();
        return item(Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(registeredItem),
                "icon.item must be registered"));
    }

    public static ObjectiveIconSpec item(String item) { return item(parse(item, "icon.item")); }
    public static ObjectiveIconSpec item(ResourceLocation item) {
        return new ObjectiveIconSpec(ObjectiveIconSpec.Mode.ITEM, null, null, null, item);
    }

    public static ObjectiveIconSpec texture(String texture) { return texture(parse(texture, "icon.texture")); }
    public static ObjectiveIconSpec texture(ResourceLocation texture) {
        return new ObjectiveIconSpec(ObjectiveIconSpec.Mode.TEXTURE, texture, null, null);
    }

    public static ObjectiveIconSpec provider(String provider) { return provider(parse(provider, "icon.provider")); }
    public static ObjectiveIconSpec provider(ResourceLocation provider) {
        return new ObjectiveIconSpec(ObjectiveIconSpec.Mode.PROVIDER, null, null, provider);
    }

    /** Missing legacy JSON fields are AUTO; explicit builder null arguments remain errors. */
    public static ObjectiveIconSpec normalize(@Nullable ObjectiveIconSpec icon) {
        if (icon == null) return auto();
        icon.validate();
        return switch (icon.mode()) {
            case AUTO -> auto();
            case NONE -> none();
            default -> icon;
        };
    }

    private static ResourceLocation parse(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        ResourceLocation id = value.isBlank() ? null : ResourceLocation.tryParse(value);
        if (id == null) throw new IllegalArgumentException(field + " must be a resource ID: " + value);
        return ObjectiveIconSpec.requireResourceId(id, field);
    }
}
