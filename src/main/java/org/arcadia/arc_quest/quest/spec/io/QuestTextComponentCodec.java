package org.arcadia.arc_quest.quest.spec.io;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

/** Native text trees keep translation arguments, fallbacks, siblings and styles unresolved. */
public final class QuestTextComponentCodec {
    private QuestTextComponentCodec() { }

    public static String encode(Component component) {
        return Component.Serializer.toJson(component == null ? Component.empty() : component,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    public static Component decode(String json) {
        if (json == null || json.isBlank()) throw new IllegalArgumentException("Component JSON is required");
        try {
            Component component = Component.Serializer.fromJson(json,
                    RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
            if (component == null) throw new IllegalArgumentException("Component JSON is null");
            return component;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid text component JSON", exception);
        }
    }
}
