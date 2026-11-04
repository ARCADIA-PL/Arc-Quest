package org.arcadia.arc_quest.quest.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import org.arcadia.arc_quest.quest.api.CollectionEntryDefinition;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

/** Server-only navigation SPI. Providers receive an already authorized entry. */
public final class CollectionSourceNavigationRegistry {
    private static final Map<String, Provider> PROVIDERS = new HashMap<>();

    private CollectionSourceNavigationRegistry() {}

    public record Context(ServerPlayer player, CollectionEntryDefinition entry) {}

    public record Destination(ResourceLocation dimension, Vec3 position) {
        public Destination {
            Objects.requireNonNull(dimension);
            Objects.requireNonNull(position);
            if (!Double.isFinite(position.x)
                    || !Double.isFinite(position.y)
                    || !Double.isFinite(position.z))
                throw new IllegalArgumentException("Nonfinite collection destination");
        }
    }

    public interface Provider {
        boolean supports(CollectionEntryDefinition entry);

        /** Null means a bounded asynchronous lookup is still pending or unavailable. */
        @Nullable
        Destination resolve(Context context);
    }

    public static synchronized void register(String entryNamespace, Provider provider) {
        if (PROVIDERS.putIfAbsent(entryNamespace, Objects.requireNonNull(provider)) != null)
            throw new IllegalArgumentException(
                    "Duplicate collection navigation namespace: " + entryNamespace);
    }

    @Nullable
    public static Provider provider(CollectionEntryDefinition entry) {
        var provider = PROVIDERS.get(entry.getEntryId().getNamespace());
        return provider != null && provider.supports(entry) ? provider : null;
    }
}
