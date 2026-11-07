package org.arcadia.arc_quest.quest.api.collection;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Read-only server-thread queries. Each call reauthorizes current knowledge and category conditions;
 * it never accepts a quest, observes an item or changes progress. Retain addresses, not snapshots,
 * across ticks/reloads. Hidden identities and undisclosed variants are absent.
 */
public interface CollectionViews {
    record RunRef(String questId, String runId) {
        public RunRef { Objects.requireNonNull(questId); Objects.requireNonNull(runId); }
    }
    record EntryRef(RunRef run, String phaseId, String bindingId) {}
    record EntryView(EntryRef ref, ResourceLocation entryId, String categoryId, Set<String> variantIds,
                     boolean discovered, boolean researched, boolean obtainable) {
        public EntryView { variantIds = Set.copyOf(variantIds); }
    }
    record RelationView(ResourceLocation relationId, EntryView source, EntryView target,
                        Set<String> variantIds, String method) {
        public RelationView { variantIds = Set.copyOf(variantIds); }
    }
    record Snapshot(List<EntryView> entries, List<RelationView> relations) {
        public Snapshot { entries = List.copyOf(entries); relations = List.copyOf(relations); }
        public Set<ResourceLocation> entryIds(String questId) {
            var ids = new LinkedHashSet<ResourceLocation>();
            entries.stream().filter(e -> e.ref().run().questId().equals(questId)).forEach(e -> ids.add(e.entryId()));
            return Set.copyOf(ids);
        }
        public List<RelationView> sources(EntryRef target, String variant) {
            if (variant != null && !variant.isEmpty() && entries.stream().noneMatch(e -> e.ref().equals(target) && e.variantIds().contains(variant))) return List.of();
            return relations.stream().filter(r -> r.target().ref().equals(target)
                    && (r.variantIds().isEmpty() || r.variantIds().contains(variant))).toList();
        }
        public List<RelationView> products(ResourceLocation source) {
            return relations.stream().filter(r -> r.source().entryId().equals(source)).toList();
        }
    }
    Snapshot snapshot(ServerPlayer player);
    Optional<EntryView> resolve(ServerPlayer player, EntryRef ref, String variant);
    default Set<ResourceLocation> visibleEntryIds(ServerPlayer player, String questId) {
        return snapshot(player).entryIds(questId);
    }
}
