package org.arcadia.arc_quest.quest.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.CollectionDetailDocument;
import org.arcadia.arc_quest.quest.api.CollectionEntryDefinition;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Add-ons register presentation providers at setup; only the authorized server detail service invokes them. */
public final class CollectionDetailProviderRegistry {
    private CollectionDetailProviderRegistry() {}
    public record Context(ServerPlayer player, QuestRuntimeData runtime, String phaseId,
                          String bindingId, CollectionEntryDefinition entry) {}
    @FunctionalInterface public interface Provider { CollectionDetailDocument describe(Context context); }
    private record Registration(boolean preferArchive, Provider provider) {}
    private static final Map<ResourceLocation, Registration> PROVIDERS = new ConcurrentHashMap<>();
    public static void register(ResourceLocation entryId, boolean preferArchive, Provider provider) {
        if (PROVIDERS.putIfAbsent(entryId, new Registration(preferArchive, java.util.Objects.requireNonNull(provider))) != null)
            throw new IllegalArgumentException("Duplicate collection detail provider: " + entryId);
    }
    public static boolean contains(ResourceLocation entryId) { return PROVIDERS.containsKey(entryId); }
    public static boolean prefersArchive(ResourceLocation entryId) {
        var registration = PROVIDERS.get(entryId);
        return registration != null && registration.preferArchive();
    }
    public static CollectionDetailDocument describe(Context context) {
        var registration = PROVIDERS.get(context.entry().getEntryId());
        return registration == null ? CollectionDetailDocument.EMPTY : registration.provider().describe(context);
    }
}
