package org.arcadia.arc_quest.quest.logic;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.data.CollectionRunDefinitionStore;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.registry.CollectionDemoDefinitionFactories;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** One authoritative definition and Tag candidate set for each accepted investigation run. */
public final class CollectionRunDefinitions {
    private CollectionRunDefinitions() { }

    /** Must succeed before the runtime is added to the player's active quests. */
    public static void capture(@Nullable ServerPlayer player, QuestDefinition definition, QuestRuntimeData runtime) {
        if (!definition.hasCollectionSheets() || player == null) return;
        try {
            captureInStore(CollectionRunDefinitionStore.get(player.server), definition, runtime, CollectionRunDefinitions::currentTagMembers);
        } catch (CollectionRunDefinitionStore.UnsupportedSnapshotException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new CollectionRunDefinitionStore.UnsupportedSnapshotException("Cannot preserve quest '" + definition.getId() + "': " + exception.getMessage());
        }
    }

    public static void captureInStore(CollectionRunDefinitionStore store, QuestDefinition definition,
                                      QuestRuntimeData runtime, Function<ResourceLocation, Collection<ResourceLocation>> tags) {
        if (!definition.hasCollectionSheets()) return;
        CollectionDemoDefinitionFactories.ensureRegistered();
        if (!definition.getId().toString().equals(runtime.getQuestId())) throw new IllegalArgumentException("Runtime/definition identity mismatch");
        if (runtime.hasFrozenDefinitionHash()) {
            QuestDefinition pinned = store.resolve(runtime.getFrozenDefinitionHash(), definition.getId());
            freezeTags(pinned, runtime, tags);
            return;
        }
        if (runtime.isHistoricalUnpinnedRun()) {
            resolveInStore(store, runtime, definition, tags);
            return;
        }
        String hash = store.freezeRegistered(definition);
        QuestDefinition pinned = store.resolve(hash, definition.getId());
        // Capture candidates before publishing the hash so failures cannot leave half-initialized active runs.
        freezeTags(pinned, runtime, tags);
        var capability = CollectionRunDefinitionStore.capability(definition);
        runtime.setFrozenDefinition(hash, capability.version().isEmpty() ? "authoring-json-v1" : capability.version());
    }

    @Nullable public static QuestDefinition resolve(@Nullable MinecraftServer server, QuestRuntimeData runtime) {
        return resolve(server, runtime, QuestRegistry.getServerDefinition(ResourceLocation.tryParse(runtime.getQuestId())));
    }

    @Nullable public static QuestDefinition resolve(@Nullable MinecraftServer server, QuestRuntimeData runtime,
                                                     @Nullable QuestDefinition live) {
        CollectionDemoDefinitionFactories.ensureRegistered();
        if (!runtime.hasFrozenDefinitionHash() && !requiresFrozenDefinition(runtime, live)) return live;
        if (server == null) {
            if (runtime.hasFrozenDefinitionHash()) throw unavailable(runtime, "Frozen definition requires its server-world store");
            if (runtime.isHistoricalUnpinnedRun()) {
                ResourceLocation id = ResourceLocation.tryParse(runtime.getQuestId());
                if (id != null && CollectionDemoDefinitionFactories.isHistoricalDemo(id)) return CollectionDemoDefinitionFactories.legacyDefinition(id);
                throw unavailable(runtime, "Unversioned historical investigation has no original definition");
            }
            return live; // Pure new-run reducers have no world persistence to resolve.
        }
        return resolveInStore(CollectionRunDefinitionStore.get(server), runtime, live);
    }

    @Nullable public static QuestDefinition resolveInStore(CollectionRunDefinitionStore store, QuestRuntimeData runtime,
                                                            @Nullable QuestDefinition live) {
        return resolveInStore(store, runtime, live, CollectionRunDefinitions::currentTagMembers);
    }

    @Nullable public static QuestDefinition resolveInStore(CollectionRunDefinitionStore store, QuestRuntimeData runtime,
                                                            @Nullable QuestDefinition live,
                                                            Function<ResourceLocation, Collection<ResourceLocation>> tags) {
        ResourceLocation id = ResourceLocation.tryParse(runtime.getQuestId());
        if (id == null) throw unavailable(runtime, "Invalid quest identity");
        CollectionDemoDefinitionFactories.ensureRegistered();
        if (runtime.hasFrozenDefinitionHash()) {
            QuestDefinition pinned = store.resolve(runtime.getFrozenDefinitionHash(), id);
            freezeTags(pinned, runtime, tags);
            return pinned;
        }
        if (runtime.isHistoricalUnpinnedRun() && requiresFrozenDefinition(runtime, live)) {
            // Never freeze current rules as an unversioned old run: an explicit original factory is required.
            String originalVersion = CollectionRunDefinitionStore.getUnversionedLegacyVersion(id);
            if (originalVersion == null)
                throw unavailable(runtime, "Original rules for this unversioned historical investigation are unavailable; provide an explicit old factory");
            String hash = store.freezeLegacyCode(id, originalVersion);
            QuestDefinition pinned = store.resolve(hash, id);
            freezeTags(pinned, runtime, tags);
            runtime.setFrozenDefinition(hash, originalVersion);
            return pinned;
        }
        if (live == null || !live.hasCollectionSheets()) return live;
        // A new runtime's caller must capture before accepting it; this path supports pure initialization only.
        if (!runtime.isHistoricalUnpinnedRun()) return live;
        throw unavailable(runtime, "Cannot infer historical rules from the current quest");
    }

    private static boolean requiresFrozenDefinition(QuestRuntimeData runtime, @Nullable QuestDefinition live) {
        if (live != null && live.hasCollectionSheets()) return true;
        if (runtime.hasCollectionData() && (!runtime.getCollectionData().getRunId().isEmpty()
                || !runtime.getCollectionData().getSheetPhaseIds().isEmpty())) return true;
        ResourceLocation id = ResourceLocation.tryParse(runtime.getQuestId());
        return runtime.isHistoricalUnpinnedRun() && id != null
                && CollectionRunDefinitionStore.getUnversionedLegacyVersion(id) != null;
    }

    public static void freezeTags(QuestDefinition definition, QuestRuntimeData runtime,
                                   Function<ResourceLocation, Collection<ResourceLocation>> tags) {
        if (runtime.isItemTagSnapshotComplete()) return;
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (PhaseDefinition phase : definition.getAllPhases()) for (ObjectiveEntry objective : phase.getObjectives()) collectTag(objective, ids);
        if (definition.getCollectionConfig() != null) for (CollectionEntryDefinition entry : definition.getCollectionConfig().getEntries()) {
            if (entry.getItemTag() != null) ids.add(entry.getItemTag());
            for (ObjectiveEntry objective : entry.getResearchObjectives()) collectTag(objective, ids);
            for (ObjectiveEntry objective : entry.getDiscoveryObjectives()) collectTag(objective, ids);
        }
        Map<ResourceLocation, Set<ResourceLocation>> pending = new LinkedHashMap<>();
        for (ResourceLocation id : ids) if (!runtime.hasFrozenItemTag(id))
            pending.put(id, Set.copyOf(Objects.requireNonNull(tags.apply(id), "Tag resolver returned null for " + id)));
        // Resolve the entire candidate set first; a failing resolver must not partially pin a run.
        pending.forEach(runtime::freezeItemTag);
        runtime.markItemTagSnapshotComplete();
    }

    private static void collectTag(ObjectiveEntry objective, Set<ResourceLocation> tags) {
        if (ObjectiveItemResolver.isItemObjective(objective) && objective.getTargetTagResourceLocation() != null)
            tags.add(objective.getTargetTagResourceLocation());
    }

    public static boolean matchesItemTag(QuestRuntimeData runtime, ResourceLocation tagId, ResourceLocation itemId) {
        return runtime.hasFrozenItemTag(tagId) ? runtime.getFrozenItemTagMembers(tagId).contains(itemId)
                : currentTagMembers(tagId).contains(itemId);
    }

    public static Collection<ResourceLocation> currentTagMembers(ResourceLocation tagId) {
        var tag = BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, tagId));
        if (tag.isEmpty()) return List.of();
        Set<ResourceLocation> members = new LinkedHashSet<>();
        tag.get().forEach(holder -> members.add(BuiltInRegistries.ITEM.getKey(holder.value())));
        return Set.copyOf(members);
    }

    private static CollectionRunDefinitionStore.UnsupportedSnapshotException unavailable(QuestRuntimeData runtime, String reason) {
        return new CollectionRunDefinitionStore.UnsupportedSnapshotException("Quest '" + runtime.getQuestId() + "': " + reason);
    }
}
